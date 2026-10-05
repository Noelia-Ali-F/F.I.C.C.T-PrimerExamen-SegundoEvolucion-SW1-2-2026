package com.arqnova.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arqnova.dto.request.UmlAiCommandRequest;
import com.arqnova.dto.response.UmlAiCommandResponse;
import com.arqnova.dto.response.UmlAiOperationResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import java.text.Normalizer;
import java.time.Duration;


import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class OpenAiUmlInterpreter {
    private static final Set<String> OPERATIONS = Set.of("CLASS_CREATE", "CLASS_UPDATE", "CLASS_DELETE",
            "ATTRIBUTE_CREATE", "ATTRIBUTE_UPDATE", "ATTRIBUTE_DELETE",
            "METHOD_CREATE", "METHOD_UPDATE", "METHOD_DELETE",
            "RELATION_CREATE", "RELATION_UPDATE", "RELATION_DELETE");
    private static final Set<String> RELATION_TYPES = Set.of("association", "aggregation", "composition", "inheritance", "dependency");
    private static final String MULTIPLICITY = "^(\\*|\\d+|\\d+\\.\\.(\\d+|\\*))$";

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${app.openai.api-key:}") private String apiKey;
    @Value("${app.openai.model:gpt-6.1-sol}") private String model;
    @Value("${app.openai.image-model:}") private String imageModel;
    @Value("${app.openai.base-url:https://api.openai.com/v1}") private String baseUrl;
    @Value("${app.openai.timeout-seconds:45}") private long timeoutSeconds;
    @Value("${app.openai.reasoning-effort:low}") private String reasoningEffort;

    public OpenAiUmlInterpreter(ObjectMapper mapper) { this.mapper = mapper; }

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    private String visionModel() {
        return imageModel == null || imageModel.isBlank() ? model : imageModel.trim();
    }

    public static class ProviderException extends IllegalStateException {
        public final int httpStatus;
        public ProviderException(int status) {
            super("OpenAI rechazó la solicitud (HTTP " + status + ")");
            this.httpStatus = status;
        }
    }

    public Map<String, Object> analyzeImage(MultipartFile image) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("El análisis de imagen no está configurado.");
        if (image == null || image.isEmpty()) throw new IllegalArgumentException("Selecciona una imagen para analizar.");
        String mime = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!Set.of("image/png", "image/jpeg", "image/webp").contains(mime))
            throw new IllegalArgumentException("Formato no admitido. Usa PNG, JPG, JPEG o WEBP.");
        if (image.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("La imagen no puede superar 10 MB.");
        String prompt = """
                Analiza esta imagen únicamente como diagrama de clases UML. Devuelve JSON conforme al schema.
                Extrae todos los elementos visibles: clases, atributos, métodos, relaciones y multiplicidades. Revisa el diagrama completo, no solo las primeras líneas.
                El compartimento inferior de una clase contiene métodos aunque estén escritos sin paréntesis. Conserva los nombres legibles; no inventes parámetros ni tipos de retorno.
                Devuelve x e y como posición de la esquina superior izquierda de cada clase, normalizada entre 0 y 1 respecto a la imagen; null si no se puede ubicar.
                Recorre cada línea desde un extremo hasta el otro, incluso si tiene codos o cambia de dirección. No omitas una relación por no ver una multiplicidad.
                Rombo negro/relleno = composition; rombo hueco = aggregation; triángulo hueco = inheritance. En composición/agregación, sourceClassName es la clase junto al rombo, y targetClassName la otra clase. Conserva la etiqueta de la línea.
                Si la multiplicidad no está escrita en un extremo, usa cadena vacía en ese extremo y añade una advertencia; no descartes la relación.
                No inventes texto ilegible. Para cualquier incertidumbre agrega una advertencia en español.
                Conserva nombres de atributos legibles aunque no tengan tipo escrito: devuelve type=null.
                No deduzcas tipos ni PK a partir del nombre del atributo.
                Si no hay al menos una clase UML reconocible devuelve status=ERROR y explica el problema.
                Usa tipos de relación: association, aggregation, composition, inheritance o dependency.
                Usa multiplicidades UML como 1, 0..1, 0..*, 1..* o *.
                Si una clase está unida mediante línea discontinua al centro de una asociación, es una clase de asociación: inclúyela en classes con sus atributos visibles y escribe su nombre exacto en associationClassName de esa relación. No la trates como clase independiente.
                Si no existe esa clase vinculada, deja associationClassName vacío. No inventes nombres ni atributos.
                isPrimaryKey solo puede ser true cuando la imagen lo indique explícitamente mediante PK, clave o un marcador inequívoco.
                """;
        OpenAiRequestTrace trace = new OpenAiRequestTrace();
        long startedAt = System.nanoTime();
        String json;
        try {
            json = sendOpenAi(prompt, imageSchema(), "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image.getBytes()), visionModel(), trace);
            log.info("OpenAI CU8 HTTP completado: durationMs={} attempts={} status={}",
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), trace.attempts, 200);
        } catch (Exception error) {
            String errorType = error instanceof HttpTimeoutException ? "TIMEOUT"
                    : error instanceof ProviderException provider ? "HTTP_" + provider.httpStatus
                    : error.getClass().getSimpleName();
            log.warn("OpenAI CU8 HTTP falló: durationMs={} attempts={} errorType={}",
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), trace.attempts, errorType);
            throw error;
        }
        if (json.isBlank()) throw new IllegalStateException("No se obtuvo un resultado del reconocimiento.");
        Map<String,Object> raw = mapper.readValue(json, new TypeReference<>() {});
        if (!Set.of("READY","ERROR").contains(String.valueOf(raw.get("status")))
                || !(raw.get("classes") instanceof List<?>) || !(raw.get("relations") instanceof List<?>)
                || !(raw.get("warnings") instanceof List<?>) || !(raw.get("message") instanceof String))
            throw new IllegalArgumentException("Respuesta visual incompatible con el schema.");
        return validateImage(raw);
    }

    public UmlAiCommandResponse interpret(UmlAiCommandRequest request) throws Exception {
        String prompt = """
                Eres un intérprete de comandos para un editor de diagramas de clases UML. Devuelve exclusivamente JSON conforme al schema.
                Interpreta español natural y permite varias operaciones ordenadas. No inventes elementos existentes.
                Si falta información esencial o hay ambigüedad, status=CLARIFICATION, operations=[] y formula clarificationQuestion.
                Tipos permitidos: CLASS_CREATE, CLASS_UPDATE, CLASS_DELETE, ATTRIBUTE_CREATE, ATTRIBUTE_UPDATE,
                ATTRIBUTE_DELETE, METHOD_CREATE, METHOD_UPDATE, METHOD_DELETE,
                RELATION_CREATE, RELATION_UPDATE, RELATION_DELETE.
                Para crear clase usa target.className y parameters.attributes=[{name,type,isPrimaryKey}].
                Para atributos usa target.className y target.attributeName.
                Para métodos usa target.className y target.methodName; parameters puede incluir name, parameters y returnType.
                Para relaciones usa target.sourceClassName y target.targetClassName.
                Tipos de relación: association, aggregation, composition, inheritance, dependency.
                Multiplicidades válidas: *, número, n..m o n..*.
                Reconoce expresiones como entidad/clase, atributo/campo, identificador/PK, uno a muchos, renombrar y relacionar.
                Nunca devuelvas código, SQL, JavaScript ni comandos. Solo operaciones UML del conjunto cerrado.
                Modelo UML actual:
                %s
                Instrucción del usuario:
                %s
                """.formatted(mapper.writeValueAsString(request.getModel()), request.getInstruction());

        String json = sendOpenAi(prompt, schema(), null, model, null);
        if (json.isBlank()) throw new IllegalStateException("OpenAi devolvió una respuesta vacía");
        log.debug("OpenAI UML JSON estructurado bruto (modelo={}): {}", model, json);
        try {
            Map<String, Object> raw = mapper.readValue(json, new TypeReference<>() {});
            UmlAiCommandResponse result = validate(raw, request.getModel());
            if ("ERROR".equals(result.getStatus())) {
                log.warn("OpenAI UML produjo una salida rechazada por validación (modelo={}): {}",
                        model, result.getMessage());
            }
            return result;
        } catch (RuntimeException parsingError) {
            log.warn("No se pudo parsear/validar la salida estructurada de OpenAI usando el modelo {}: {}",
                    model, parsingError.getMessage());
            throw parsingError;
        }
    }

    private static final class OpenAiRequestTrace { int attempts; }

    private String sendOpenAi(String prompt, Map<String,Object> schema, String imageUrl, String selectedModel, OpenAiRequestTrace trace) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("OpenAI no está configurado.");
        List<Map<String,Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", prompt + "\nUsa null para parámetros no aplicables; no los inventes."));
        if (imageUrl != null) content.add(Map.of("type", "input_image", "image_url", imageUrl));
        Map<String,Object> outputSchema = strictSchema(mapper.valueToTree(schema), imageUrl == null);
        Map<String,Object> payload = Map.of("model", selectedModel,
                "reasoning", Map.of("effort", reasoningEffort), "store", false,
                "max_output_tokens", 8192,
                "input", List.of(Map.of("role", "user", "content", content)),
                "text", Map.of("format", Map.of("type", "json_schema", "name", imageUrl != null ? "uml_image" : "uml_command", "strict", true, "schema", outputSchema)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
        if (trace != null) trace.attempts++;
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ProviderException(response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        if (!"completed".equals(root.path("status").asText())) throw new IllegalStateException("OpenAI no completó la respuesta.");
        StringBuilder output = new StringBuilder();
        for (JsonNode item : root.path("output")) for (JsonNode part : item.path("content")) {
            if ("refusal".equals(part.path("type").asText())) throw new IllegalStateException("OpenAI no pudo interpretar la solicitud.");
            if ("output_text".equals(part.path("type").asText())) output.append(part.path("text").asText());
        }
        if (output.isEmpty()) throw new IllegalStateException("OpenAI devolvió una respuesta vacía.");
        return output.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> strictSchema(JsonNode original, boolean command) {
        Map<String,Object> schema = mapper.convertValue(original, new TypeReference<>() {});
        if (command) {
            Map<String,Object> props = new LinkedHashMap<>();
            for (String name : List.of("name", "type", "parameters", "returnType", "label", "sourceMultiplicity", "targetMultiplicity", "associationClassName"))
                props.put(name, Map.of("type", List.of("string", "null")));
            props.put("isPrimaryKey", Map.of("type", List.of("boolean", "null")));
            props.put("attributes", Map.of("type", List.of("array", "null"), "items", Map.of("type", "object", "properties", Map.of(
                    "name", Map.of("type", "string"), "type", Map.of("type", "string"), "isPrimaryKey", Map.of("type", "boolean")))));
            Map<String,Object> rootProps = (Map<String,Object>)schema.get("properties");
            Map<String,Object> operations = (Map<String,Object>)rootProps.get("operations");
            Map<String,Object> items = (Map<String,Object>)operations.get("items");
            ((Map<String,Object>)items.get("properties")).put("parameters", new LinkedHashMap<>(Map.of("type", "object", "properties", props)));
        }
        schema = mapper.convertValue(schema, new TypeReference<>() {});
        closeObjects(schema);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private void closeObjects(Map<String,Object> schema) {
        if (schema.get("properties") instanceof Map<?,?> properties) {
            schema.put("additionalProperties", false);
            schema.put("required", new ArrayList<>(properties.keySet()));
            for (Object property : properties.values()) closeObjects((Map<String,Object>)property);
        }
        if (schema.get("items") instanceof Map<?,?> items) closeObjects((Map<String,Object>)items);
    }

    private Map<String, Object> schema() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
        Map<String, Object> target = Map.of("type", "object", "properties", Map.of(
                "className", nullableString, "attributeName", nullableString, "methodName", nullableString,
                "sourceClassName", nullableString, "targetClassName", nullableString),
                "required", List.of("className", "attributeName", "methodName", "sourceClassName", "targetClassName"));
        Map<String, Object> operationType = Map.of("type", "string", "enum", List.of(
                "CLASS_CREATE", "CLASS_UPDATE", "CLASS_DELETE",
                "ATTRIBUTE_CREATE", "ATTRIBUTE_UPDATE", "ATTRIBUTE_DELETE",
                "METHOD_CREATE", "METHOD_UPDATE", "METHOD_DELETE",
                "RELATION_CREATE", "RELATION_UPDATE", "RELATION_DELETE"));
        Map<String, Object> operation = Map.of("type", "object", "properties", Map.of(
                "type", operationType, "target", target,
                "parameters", Map.of("type", "object", "additionalProperties", true)),
                "required", List.of("type", "target", "parameters"));
        return Map.of("type", "object", "properties", Map.of(
                "status", Map.of("type", "string", "enum", List.of("READY", "CLARIFICATION", "ERROR")),
                "operations", Map.of("type", "array", "items", operation),
                "clarificationQuestion", nullableString, "message", string),
                "required", List.of("status", "operations", "clarificationQuestion", "message"));
    }

    private Map<String,Object> imageSchema() {
        Map<String,Object> string=Map.of("type","string");
        Map<String,Object> attribute=Map.of("type","object","properties",Map.of(
                "name",string,"type",Map.of("type",List.of("string","null")),"isPrimaryKey",Map.of("type","boolean")),
                "required",List.of("name","type","isPrimaryKey"));
        Map<String,Object> method=Map.of("type","object","properties",Map.of("name",string,"parameters",string,"returnType",Map.of("type",List.of("string","null"))));
        Map<String,Object> coordinate=Map.of("type",List.of("number","null"));
        Map<String,Object> clazz=Map.of("type","object","properties",Map.of(
                "name",string,"attributes",Map.of("type","array","items",attribute),"methods",Map.of("type","array","items",method),"x",coordinate,"y",coordinate),
                "required",List.of("name","attributes","methods","x","y"));
        Map<String,Object> relation=Map.of("type","object","properties",Map.of(
                "sourceClassName",string,"targetClassName",string,
                "type",Map.of("type","string","enum",List.copyOf(RELATION_TYPES)),"label",string,
                "sourceMultiplicity",string,"targetMultiplicity",string,"associationClassName",string),
                "required",List.of("sourceClassName","targetClassName","type","label","sourceMultiplicity","targetMultiplicity","associationClassName"));
        return Map.of("type","object","properties",Map.of(
                "status",Map.of("type","string","enum",List.of("READY","ERROR")),
                "classes",Map.of("type","array","items",clazz),
                "relations",Map.of("type","array","items",relation),
                "warnings",Map.of("type","array","items",string),"message",string),
                "required",List.of("status","classes","relations","warnings","message"));
    }

    @SuppressWarnings("unchecked")
    Map<String,Object> validateImage(Map<String,Object> raw) {
        String status=text(raw.get("status")).toUpperCase(Locale.ROOT),message=cleanText(raw.get("message"),500);
        List<String> warnings=new ArrayList<>();
        if(raw.get("warnings") instanceof List<?> list) for(Object item:list){String warning=cleanText(item,240);if(!warning.isBlank())warnings.add(warning);}
        if(!"READY".equals(status)) return Map.of("status","ERROR","classes",List.of(),"relations",List.of(),"warnings",warnings,"message",message.isBlank()?"No se reconoció un diagrama UML legible.":message,"provider","OPENAI","model",visionModel());
        if(!(raw.get("classes") instanceof List<?> rawClasses)) throw new IllegalArgumentException("La respuesta visual no contiene clases válidas.");
        List<Map<String,Object>> classes=new ArrayList<>();
        for(Object item:rawClasses){if(!(item instanceof Map<?,?> map))continue;String name=validName(map.get("name"));if(name==null||exists(classes,name)){warnings.add("Se descartó una clase con nombre ilegible o duplicado.");continue;}List<Map<String,Object>> attrs=validateImageAttributes(map.get("attributes"),warnings);Map<String,Object> clazz=new LinkedHashMap<>(Map.of("name",name,"attributes",attrs,"methods",validateImageMethods(map.get("methods"),warnings)));for(String axis:List.of("x","y")){Object coordinate=map.get(axis);if(coordinate instanceof Number value&&Double.isFinite(value.doubleValue())&&value.doubleValue()>=0&&value.doubleValue()<=1)clazz.put(axis,value.doubleValue());}classes.add(clazz);}
        if(classes.isEmpty()) return Map.of("status","ERROR","classes",List.of(),"relations",List.of(),"warnings",warnings,"message","No se reconoció ninguna clase UML con suficiente claridad.","provider","OPENAI","model",visionModel());
        List<Map<String,Object>> relations=new ArrayList<>();
        if(raw.get("relations") instanceof List<?> rawRelations) for(Object item:rawRelations){if(!(item instanceof Map<?,?> any))continue;Map<String,Object> relation=(Map<String,Object>)any;Map<String,Object> source=findUnique(classes,text(relation.get("sourceClassName"))),target=findUnique(classes,text(relation.get("targetClassName")));Map<String,Object> clean=new LinkedHashMap<>();clean.put("sourceClassName",source==null?"":source.get("name"));clean.put("targetClassName",target==null?"":target.get("name"));clean.put("type",text(relation.get("type")));clean.put("label",cleanText(relation.get("label"),120));clean.put("sourceMultiplicity",imageMultiplicity(relation.get("sourceMultiplicity"),warnings));clean.put("targetMultiplicity",imageMultiplicity(relation.get("targetMultiplicity"),warnings));if(source==null||target==null||!validRelation(clean)){warnings.add("Se descartó una relación con extremos o multiplicidades poco claros.");continue;}String associationClassName=text(relation.get("associationClassName"));Map<String,Object> associationClass=associationClassName.isBlank()?null:findUnique(classes,associationClassName);clean.put("associationClassName",associationClass==null?"":associationClass.get("name"));relations.add(clean);}
        Map<String,Object> result=new LinkedHashMap<>();result.put("status","READY");result.put("classes",classes);result.put("relations",relations);result.put("warnings",warnings);result.put("message",message.isBlank()?"Imagen analizada. Revisa el resultado antes de insertarlo.":message);result.put("provider","OPENAI");result.put("model",visionModel());return result;
    }

    private String imageMultiplicity(Object value,List<String> warnings){
        String multiplicity=text(value).replaceAll("\\s+", "").replaceAll("\\.{3,}","..");
        if(multiplicity.isBlank()){warnings.add("Una multiplicidad no está indicada en la imagen; se usa 1 provisionalmente y puede editarse en el Inspector.");return "1";}
        return multiplicity;
    }
    private List<Map<String,Object>> validateImageMethods(Object value,List<String> warnings){
        List<Map<String,Object>> methods=new ArrayList<>();
        if(value instanceof List<?> list)for(Object item:list){if(!(item instanceof Map<?,?> method))continue;String name=validName(method.get("name"));if(name==null||exists(methods,name)){warnings.add("Se descartó un método ilegible o duplicado.");continue;}String returnType=text(method.get("returnType"));methods.add(new LinkedHashMap<>(Map.of("name",name,"parameters",cleanText(method.get("parameters"),500),"returnType",returnType.isBlank()?"":returnType)));}
        return methods;
    }

    @SuppressWarnings("unchecked")
    private UmlAiCommandResponse validate(Map<String, Object> raw, Map<String, Object> sourceModel) {
        String status = text(raw.get("status")).toUpperCase(Locale.ROOT);
        String message = cleanText(raw.get("message"), 500);
        String question = cleanText(raw.get("clarificationQuestion"), 300);
        if ("CLARIFICATION".equals(status)) {
            if (question.isBlank()) return invalid("La aclaración de OpenAI no contiene una pregunta.");
            return response(status, List.of(), question, message.isBlank() ? question : message);
        }
        if ("ERROR".equals(status)) return response(status, List.of(), null, message.isBlank() ? "No se pudo interpretar la instrucción." : message);
        if (!"READY".equals(status)) return invalid("OpenAi devolvió un estado no permitido.");
        if (!(raw.get("operations") instanceof List<?> rawOperations) || rawOperations.isEmpty()) return invalid("La respuesta no contiene operaciones UML.");

        List<Map<String, Object>> classes = copyList(sourceModel.get("classes"));
        List<Map<String, Object>> relations = copyList(sourceModel.get("relations"));
        List<UmlAiOperationResponse> operations = new ArrayList<>();
        for (Object value : rawOperations) {
            if (!(value instanceof Map<?, ?> any)) return invalid("Una operación tiene estructura inválida.");
            Map<String, Object> op = (Map<String, Object>) any;
            String type = text(op.get("type")).toUpperCase(Locale.ROOT);
            if (!OPERATIONS.contains(type)) return invalid("OpenAi propuso una operación UML no permitida.");
            Map<String, String> target = stringMap(op.get("target"));
            Map<String, Object> parameters = objectMap(op.get("parameters"));
            parameters.values().removeIf(java.util.Objects::isNull);
            UmlAiOperationResponse resolved = resolve(type, target, parameters, classes, relations);
            if (resolved == null) return invalid("La operación contiene referencias inexistentes, ambiguas o valores inválidos.");
            operations.add(resolved);
        }
        return response("READY", operations, null, message.isBlank() ? "Instrucción interpretada correctamente." : message);
    }

    private UmlAiOperationResponse resolve(String type, Map<String, String> target, Map<String, Object> p,
                                            List<Map<String, Object>> classes, List<Map<String, Object>> relations) {
        String targetId = null, elementType;
        switch (type) {
            case "CLASS_CREATE" -> {
                String name = validName(first(target.get("className"), p.get("name"))); if (name == null || exists(classes, name)) return null;
                targetId = "class-ai-" + UUID.randomUUID();
                List<Map<String,Object>> attrs = validateAttributes(p.get("attributes")); if (attrs == null) return null;
                p=new LinkedHashMap<>();p.put("name",name);p.put("id",targetId);p.put("attributes",attrs);
                classes.add(new LinkedHashMap<>(Map.of("id", targetId, "name", name, "attributes", attrs, "methods", new ArrayList<>()))); elementType = "class";
            }
            case "CLASS_UPDATE", "CLASS_DELETE" -> {
                Map<String,Object> clazz=findUnique(classes,target.get("className"));if(clazz==null)return null;targetId=text(clazz.get("id"));elementType="class";
                if(type.equals("CLASS_UPDATE")){String name=validName(p.get("name"));if(name==null)return null;Map<String,Object> duplicate=findUnique(classes,name);if(duplicate!=null&&!targetId.equals(text(duplicate.get("id"))))return null;clazz.put("name",name);p=new LinkedHashMap<>(Map.of("name",name));}
                else {String deletedId=targetId;classes.removeIf(item->deletedId.equals(text(item.get("id"))));relations.removeIf(item->deletedId.equals(text(item.get("sourceId")))||deletedId.equals(text(item.get("targetId"))));p=new LinkedHashMap<>();}
            }
            case "ATTRIBUTE_CREATE" -> {
                Map<String,Object> clazz=findUnique(classes,target.get("className"));if(clazz==null)return null;String name=validName(first(target.get("attributeName"),p.get("name"))),dataType=validType(p.get("type"));if(name==null||dataType==null||exists(attributes(clazz),name))return null;targetId="attr-ai-"+UUID.randomUUID();boolean pk=Boolean.TRUE.equals(p.get("isPrimaryKey"));p=new LinkedHashMap<>();p.put("classId",clazz.get("id"));p.put("name",name);p.put("type",dataType);p.put("isPrimaryKey",pk);attributes(clazz).add(new LinkedHashMap<>(Map.of("id",targetId,"name",name,"type",dataType,"isPrimaryKey",pk)));elementType="attribute";
            }
            case "ATTRIBUTE_UPDATE", "ATTRIBUTE_DELETE" -> {
                Map<String,Object> clazz=findUnique(classes,target.get("className"));if(clazz==null)return null;Map<String,Object> attr=findUnique(attributes(clazz),target.get("attributeName"));if(attr==null)return null;targetId=text(attr.get("id"));p.put("classId",clazz.get("id"));elementType="attribute";
                if(type.equals("ATTRIBUTE_UPDATE")){Map<String,Object> clean=new LinkedHashMap<>();clean.put("classId",clazz.get("id"));if(p.containsKey("name")){String name=validName(p.get("name"));if(name==null)return null;clean.put("name",name);}if(p.containsKey("type")){String dataType=validType(p.get("type"));if(dataType==null)return null;clean.put("type",dataType);}if(p.containsKey("isPrimaryKey")){if(!(p.get("isPrimaryKey") instanceof Boolean))return null;clean.put("isPrimaryKey",p.get("isPrimaryKey"));}if(clean.size()==1)return null;p=clean;}
                else {String deletedId=targetId;attributes(clazz).removeIf(item->deletedId.equals(text(item.get("id"))));p=new LinkedHashMap<>(Map.of("classId",clazz.get("id")));}
            }
            case "METHOD_CREATE" -> {
                Map<String,Object> clazz=findUnique(classes,target.get("className"));if(clazz==null)return null;
                String name=validName(first(target.get("methodName"),p.get("name")));
                String returnType=validType(p.getOrDefault("returnType","void"));
                String parameters=cleanText(p.get("parameters"),500);
                if(name==null||returnType==null||exists(methods(clazz),name))return null;
                targetId="method-ai-"+UUID.randomUUID();
                p=new LinkedHashMap<>();p.put("classId",clazz.get("id"));p.put("name",name);p.put("parameters",parameters);p.put("returnType",returnType);
                methods(clazz).add(new LinkedHashMap<>(Map.of("id",targetId,"name",name,"parameters",parameters,"returnType",returnType)));
                elementType="method";
            }
            case "METHOD_UPDATE", "METHOD_DELETE" -> {
                Map<String,Object> clazz=findUnique(classes,target.get("className"));if(clazz==null)return null;
                Map<String,Object> method=findUnique(methods(clazz),target.get("methodName"));if(method==null)return null;
                targetId=text(method.get("id"));elementType="method";
                if(type.equals("METHOD_UPDATE")){
                    Map<String,Object> clean=new LinkedHashMap<>();clean.put("classId",clazz.get("id"));
                    if(p.containsKey("name")){String name=validName(p.get("name"));if(name==null)return null;Map<String,Object> duplicate=findUnique(methods(clazz),name);if(duplicate!=null&&!targetId.equals(text(duplicate.get("id"))))return null;clean.put("name",name);}
                    if(p.containsKey("parameters"))clean.put("parameters",cleanText(p.get("parameters"),500));
                    if(p.containsKey("returnType")){String returnType=validType(p.get("returnType"));if(returnType==null)return null;clean.put("returnType",returnType);}
                    if(clean.size()==1)return null;method.putAll(clean);p=clean;
                }else{String deletedId=targetId;methods(clazz).removeIf(item->deletedId.equals(text(item.get("id"))));p=new LinkedHashMap<>(Map.of("classId",clazz.get("id")));}
            }
            case "RELATION_CREATE" -> {
                Map<String,Object> source=findUnique(classes,target.get("sourceClassName")),dest=findUnique(classes,target.get("targetClassName"));if(source==null||dest==null)return null;targetId="rel-ai-"+UUID.randomUUID();Map<String,Object> clean=new LinkedHashMap<>();clean.put("sourceId",source.get("id"));clean.put("targetId",dest.get("id"));clean.put("type",p.getOrDefault("type","association"));clean.put("label",cleanText(p.get("label"),120));clean.put("sourceMultiplicity",p.getOrDefault("sourceMultiplicity","1"));clean.put("targetMultiplicity",p.getOrDefault("targetMultiplicity","0..*"));if(!validRelation(clean))return null;clean.put("id",targetId);p=clean;relations.add(new LinkedHashMap<>(p));elementType="relation";
            }
            case "RELATION_UPDATE", "RELATION_DELETE" -> {
                Map<String,Object> source=findUnique(classes,target.get("sourceClassName")),dest=findUnique(classes,target.get("targetClassName"));if(source==null||dest==null)return null;Map<String,Object> relation=findRelation(relations,text(source.get("id")),text(dest.get("id")));if(relation==null)return null;targetId=text(relation.get("id"));elementType="relation";if(type.equals("RELATION_UPDATE")&&!validRelationPatch(p))return null;
                if(type.equals("RELATION_UPDATE")){Map<String,Object> clean=new LinkedHashMap<>();if(p.containsKey("type"))clean.put("type",p.get("type"));if(p.containsKey("label"))clean.put("label",cleanText(p.get("label"),120));if(p.containsKey("sourceMultiplicity"))clean.put("sourceMultiplicity",p.get("sourceMultiplicity"));if(p.containsKey("targetMultiplicity"))clean.put("targetMultiplicity",p.get("targetMultiplicity"));if(!validRelationPatch(clean))return null;p=clean;relation.putAll(clean);}else{String deletedId=targetId;relations.removeIf(item->deletedId.equals(text(item.get("id"))));p=new LinkedHashMap<>();}
            }
            default -> { return null; }
        }
        return UmlAiOperationResponse.builder().type(type).targetId(targetId).elementType(elementType).target(target).parameters(p).build();
    }

    private boolean validRelation(Map<String,Object> p){p.putIfAbsent("type","association");p.putIfAbsent("label","");p.putIfAbsent("sourceMultiplicity","1");p.putIfAbsent("targetMultiplicity","0..*");return RELATION_TYPES.contains(text(p.get("type")))&&text(p.get("sourceMultiplicity")).matches(MULTIPLICITY)&&text(p.get("targetMultiplicity")).matches(MULTIPLICITY);}
    private boolean validRelationPatch(Map<String,Object> p){if(p.isEmpty())return false;if(p.containsKey("type")&&!RELATION_TYPES.contains(text(p.get("type"))))return false;return (!p.containsKey("sourceMultiplicity")||text(p.get("sourceMultiplicity")).matches(MULTIPLICITY))&&(!p.containsKey("targetMultiplicity")||text(p.get("targetMultiplicity")).matches(MULTIPLICITY));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> validateAttributes(Object value){if(value==null)return new ArrayList<>();if(!(value instanceof List<?> list))return null;List<Map<String,Object>> result=new ArrayList<>();for(Object item:list){if(!(item instanceof Map<?,?> map))return null;String name=validName(map.get("name")),type=validType(map.get("type"));if(name==null||type==null)return null;result.add(new LinkedHashMap<>(Map.of("id","attr-ai-"+UUID.randomUUID(),"name",name,"type",type,"isPrimaryKey",Boolean.TRUE.equals(map.get("isPrimaryKey")))));}return result;}
    private String validName(Object value){String result=cleanText(value,80);return result.matches("[\\p{L}_][\\p{L}\\p{N}_ -]*")?result:null;}
    private List<Map<String,Object>> validateImageAttributes(Object value,List<String> warnings) {
        List<Map<String,Object>> result=new ArrayList<>();
        if (!(value instanceof List<?> list)) { warnings.add("Los atributos requieren revisión manual."); return result; }
        for (Object item:list) {
            if (!(item instanceof Map<?,?> map)) { warnings.add("Se descartó un atributo con estructura inválida."); continue; }
            String name=validName(map.get("name"));
            if(name==null||exists(result,name)) { warnings.add("Se descartó un atributo con nombre inválido o duplicado."); continue; }
            Object rawType=map.get("type");
            if(rawType!=null&&!(rawType instanceof String)) { warnings.add("Se descartó un atributo con estructura de tipo inválida."); continue; }
            String type=rawType==null||text(rawType).isBlank()?"":validType(rawType);
            if(type==null) { warnings.add("Se descartó un atributo con tipo inválido."); continue; }
            if(type.isEmpty()&&!warnings.contains("Los tipos de estos atributos no pudieron determinarse y requieren revisión manual."))
                warnings.add("Los tipos de estos atributos no pudieron determinarse y requieren revisión manual.");
            result.add(new LinkedHashMap<>(Map.of("name",name,"type",type,"isPrimaryKey",Boolean.TRUE.equals(map.get("isPrimaryKey")))));
        }
        return result;
    }
    private String validType(Object value){String result=cleanText(value,80);return result.matches("[\\p{L}_][\\p{L}\\p{N}_<>, ?\\[\\].-]*")?result:null;}
    private Map<String,Object> findUnique(List<Map<String,Object>> list,String name){if(name==null)return null;List<Map<String,Object>> found=list.stream().filter(i->normalize(text(i.get("name"))).equals(normalize(name))).toList();return found.size()==1?found.get(0):null;}
    private boolean exists(List<Map<String,Object>> list,String name){return list.stream().anyMatch(i->normalize(text(i.get("name"))).equals(normalize(name)));}
    private Map<String,Object> findRelation(List<Map<String,Object>> list,String a,String b){List<Map<String,Object>> found=list.stream().filter(i->(a.equals(text(i.get("sourceId")))&&b.equals(text(i.get("targetId"))))||(b.equals(text(i.get("sourceId")))&&a.equals(text(i.get("targetId"))))).toList();return found.size()==1?found.get(0):null;}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> attributes(Map<String,Object> clazz){return (List<Map<String,Object>>)clazz.computeIfAbsent("attributes",k->new ArrayList<>());}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> methods(Map<String,Object> clazz){return (List<Map<String,Object>>)clazz.computeIfAbsent("methods",k->new ArrayList<>());}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> copyList(Object value){return value instanceof List<?>?mapper.convertValue(value,new TypeReference<>(){}):new ArrayList<>();}
    @SuppressWarnings("unchecked") private Map<String,Object> objectMap(Object value){return value instanceof Map<?,?>?new LinkedHashMap<>((Map<String,Object>)value):new LinkedHashMap<>();}
    @SuppressWarnings("unchecked") private Map<String,String> stringMap(Object value){Map<String,String> result=new LinkedHashMap<>();if(value instanceof Map<?,?> map)map.forEach((k,v)->{if(v!=null)result.put(text(k),cleanText(v,80));});return result;}
    private Object first(Object a,Object b){return a==null||text(a).isBlank()?b:a;}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private String cleanText(Object value,int max){String text=text(value).replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");return text.length()>max?text.substring(0,max):text;}
    private String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private UmlAiCommandResponse invalid(String message){return response("ERROR",List.of(),null,message);}
    private UmlAiCommandResponse response(String status,List<UmlAiOperationResponse> operations,String question,String message){UmlAiOperationResponse first=operations.isEmpty()?null:operations.get(0);return UmlAiCommandResponse.builder().status(status).operations(operations).clarificationQuestion(question).message(message).interpreter("OPENAI").fallbackUsed(false).operation(first==null?null:first.getType()).targetId(first==null?null:first.getTargetId()).parameters(first==null?Map.of():first.getParameters()).build();}
}
