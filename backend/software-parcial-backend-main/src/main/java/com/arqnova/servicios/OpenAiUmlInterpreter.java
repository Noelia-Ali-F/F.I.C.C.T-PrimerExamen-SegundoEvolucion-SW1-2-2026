package com.arqnova.servicios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arqnova.modelo.dto.request.UmlAiCommandRequest;
import com.arqnova.modelo.dto.response.UmlAiCommandResponse;
import com.arqnova.modelo.dto.response.UmlAiOperationResponse;
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
    @org.springframework.beans.factory.annotation.Autowired(required=false) private UmlAiProviderPipeline providerPipeline;
    private final ThreadLocal<UmlAiProviderPipeline.Result> providerResult = new ThreadLocal<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${app.openai.api-key:}") private String apiKey;
    @Value("${app.openai.model:gpt-6.1-sol}") private String model="gpt-6.1-sol";
    @Value("${app.openai.image-model:}") private String imageModel;
    @Value("${app.openai.base-url:https://api.openai.com/v1}") private String baseUrl;
    @Value("${app.openai.timeout-seconds:45}") private long timeoutSeconds;
    @Value("${app.openai.reasoning-effort:low}") private String reasoningEffort;

    public OpenAiUmlInterpreter(ObjectMapper mapper) { this.mapper = mapper; }

    public boolean isConfigured() { return (providerPipeline != null && providerPipeline.isConfigured()) || (apiKey != null && !apiKey.isBlank()); }

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
                Transcribe TODO el diagrama de clases UML visible, sin rediseñarlo ni convertirlo en tablas relacionales. Devuelve JSON compacto conforme al schema.
                Conserva literalmente nombres, idioma, mayúsculas y letras finales; no traduzcas ni corrijas palabras por convenciones. Una l manuscrita final no es el borde del recuadro.
                Incluye clases, atributos y métodos. El compartimento inferior contiene métodos aunque no haya paréntesis. Sin tipos visibles devuelve null; sin marcador PK explícito devuelve isPrimaryKey=false, incluso para Id. No inventes parámetros ni retornos.
                Recorre cada línea completa, también sus codos. Clasifica por el símbolo de ESA línea: rombo cerrado relleno=composition, hueco=aggregation, triángulo hueco=inheritance, línea simple=association, flecha discontinua=dependency. No confundas bordes, codos o números con rombos; si hay duda conserva association y advierte. En composition/aggregation el origen es la clase tocada por el rombo.
                Cada multiplicidad pertenece al recuadro físicamente junto al que está escrita: sourceMultiplicity junto a sourceClassName y targetMultiplicity junto a targetClassName. No las inviertas por lógica del dominio ni simplifiques 1..* a *. Si falta una, devuelve cadena vacía y advierte.
                Una clase conectada por línea discontinua al CENTRO de una asociación es clase de asociación, con cualquier nombre: inclúyela en classes y escribe su nombre en associationClassName de la asociación ORIGINAL, conservando ambos extremos y multiplicidades. No sustituyas ese vínculo por dos asociaciones a la clase intermedia. Sin esa clase, associationClassName vacío.
                x,y son la esquina superior izquierda de cada clase dividida por el ancho/alto de la foto original, entre 0 y 1; null si no se ubica. Sin clases legibles devuelve status=ERROR. warnings breves solo para incertidumbres reales; no expliques símbolos claros ni inventes texto ilegible.
                """;
        OpenAiRequestTrace trace = new OpenAiRequestTrace();
        long startedAt = System.nanoTime();
        String json;
        try {
            json = sendOpenAi(prompt, imageSchema(), "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image.getBytes()), visionModel(), trace);
            log.info("UML IA HTTP completado: durationMs={} attempts={} status={}",
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), trace.attempts, 200);
        } catch (Exception error) {
            String errorType = error instanceof HttpTimeoutException ? "TIMEOUT"
                    : error instanceof ProviderException provider ? "HTTP_" + provider.httpStatus
                    : error.getClass().getSimpleName();
            log.warn("UML IA HTTP falló: durationMs={} attempts={} errorType={}",
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), trace.attempts, errorType);
            throw error;
        }
        if (json.isBlank()) throw new IllegalStateException("No se obtuvo un resultado del reconocimiento.");
        try {
            Map<String,Object> raw = mapper.readValue(json, new TypeReference<>() {});
            if (!Set.of("READY","ERROR").contains(String.valueOf(raw.get("status")))
                    || !(raw.get("classes") instanceof List<?>) || !(raw.get("relations") instanceof List<?>)
                    || !(raw.get("warnings") instanceof List<?>) || !(raw.get("message") instanceof String))
                throw new IllegalArgumentException("Respuesta visual incompatible con el schema.");
            var initialProvider=providerResult.get();
            if(initialProvider!=null && "READY".equals(raw.get("status"))) {
                verifyImageDetails(raw,image);
            }
            Map<String,Object> result=new LinkedHashMap<>(validateImage(raw));
            var used=providerResult.get();
            if(used!=null){result.put("provider",used.provider());result.put("model",used.model());result.put("fallbackUsed",used.fallbackUsed());}
            return result;
        } finally {providerResult.remove();}
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
            var used=providerResult.get();
            if(used!=null){result.setInterpreter(used.provider());result.setFallbackUsed(used.fallbackUsed());}
            providerResult.remove();
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
        providerResult.remove();
        if(providerPipeline!=null && providerPipeline.isConfigured()){
            if(trace!=null)trace.attempts++;
            var result=providerPipeline.generate(prompt,strictSchema(mapper.valueToTree(schema),imageUrl==null),imageUrl);
            providerResult.set(result);
            return result.json();
        }
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

    private void verifyImageDetails(Map<String,Object> raw,MultipartFile image) throws Exception {
        if(!(raw.get("classes") instanceof List<?> classes)||classes.isEmpty())return;
        Map<String,Object> string=Map.of("type","string"),nullable=Map.of("type",List.of("string","null"));
        Map<String,Object> relation=Map.of("type","array","prefixItems",List.of(string,string,string,string,nullable,nullable,nullable),"minItems",7,"maxItems",7);
        Map<String,Object> schema=Map.of("type","object","properties",Map.of("status",Map.of("type","string","enum",List.of("READY","UNCLEAR")),"relations",Map.of("type","array","items",relation)),"required",List.of("status","relations"));
        String prompt="Transcribe SOLO las líneas UML de esta FOTO ORIGINAL, no atributos. No rediseñes ni normalices a base de datos. Para cada conexión lee los nombres literales de sus cajas, su tipo y las multiplicidades FÍSICAMENTE pegadas a cada caja. No traduzcas ni simplifiques 1..* ni deduzcas números por el dominio. "
                +"Una caja unida por línea discontinua al CENTRO de otra asociación es su clase de asociación: escribe su nombre en el último campo, conservando el vínculo original; NO crees asociaciones binarias entre esa caja y los extremos. Esa línea al centro no es otra asociación. "
                +"Orden de extremos: en association primero izquierda/superior, segundo derecha/inferior; en composition/aggregation primero la caja junto al rombo cerrado relleno/hueco; en inheritance primero hijo y segundo padre junto al triángulo; en dependency primero origen y segundo destino de la flecha discontinua. Lee cada multiplicidad junto a SU caja, no al otro extremo. "
                +"JSON compacto: status READY si se distinguen las conexiones o UNCLEAR si no; relations son arrays [nombrePrimeraCaja,nombreSegundaCaja,tipo,etiqueta,multiplicidadPegadaAPrimeraCaja,multiplicidadPegadaASegundaCaja,nombreClaseAsociaciónOvacío]. Incluye TODAS las conexiones directas. Si una multiplicidad no está escrita usa cadena vacía. No inventes clases ni texto ilegible.";
        Map<String,Object> checked=mapper.readValue(providerPipeline.reviewImageGraph(providerResult.get(),prompt,schema,"data:"+image.getContentType()+";base64,"+Base64.getEncoder().encodeToString(image.getBytes()),Math.min(4096,256+classes.size()*96)),new TypeReference<>() {});
        if(!reconcileReviewedGraph(raw,checked)){
            raw.put("status","ERROR");raw.put("message","No se pudo confirmar la estructura de las conexiones en la foto. Usa un encuadre más cercano donde se vean líneas y multiplicidades.");
        } else {verifyAssociationAttachments(raw,image);if("READY".equals(raw.get("status"))&&"GROQ".equals(providerResult.get().provider()))reviewFinalTitleStrokes(raw,image);}
    }

    /** Inspect every possible third box around n:n, even when the first read missed a connection. */
    @SuppressWarnings("unchecked")
    private void verifyAssociationAttachments(Map<String,Object> raw,MultipartFile image) throws Exception {
        List<Map<String,Object>> candidates=associationAttachmentCandidates(raw);
        if(candidates.isEmpty())return;
        if(candidates.size()>32){raw.put("status","ERROR");raw.put("message","El diagrama requiere revisar demasiadas uniones a la vez. Analiza un encuadre más cercano por secciones.");return;}
        Map<String,Object> bool=Map.of("type","boolean");
        Map<String,Object> row=Map.of("type","array","prefixItems",List.of(Map.of("type","integer"),Map.of("type","string","enum",List.of("CENTER_DASHED","BINARY_LINES","UNCLEAR")),bool,bool),"minItems",4,"maxItems",4);
        Map<String,Object> schema=Map.of("type","object","properties",Map.of("attachments",Map.of("type","array","items",row)),"required",List.of("attachments"));
        String prompt="Inspección VISUAL de trazos UML, no diseño de base de datos. La lectura anterior puede HABER INVENTADO dos líneas diagonales. Para cada candidato A--B y caja C, mira la FOTO: ¿de C sale una línea DISCONTINUA que termina en el CENTRO de A--B, sin tocar las cajas A/B? Entonces CENTER_DASHED (clase de asociación). No conviertas eso en dos asociaciones. Si NO hay unión discontinua al centro usa BINARY_LINES, incluso si C tiene solo una o ninguna línea hasta A/B. Usa UNCLEAR solo si ves una posible unión al centro pero no puedes distinguir su trazo. Además indica por separado si realmente existe una línea dibujada C--A y C--B, sin contar el camino que pasa por el centro de A--B. Los nombres solo identifican cajas; no deduzcas nada por sus atributos. Devuelve attachments=[[indice,veredicto,hayLineaDirectaCA,hayLineaDirectaCB]], una fila por candidato. Candidatos: "+mapper.writeValueAsString(candidates);
        var checked=mapper.readValue(providerPipeline.reviewImageGraph(providerResult.get(),prompt,schema,"data:"+image.getContentType()+";base64,"+Base64.getEncoder().encodeToString(image.getBytes()),Math.min(2048,128+candidates.size()*48)),new TypeReference<Map<String,Object>>() {});
        if(!applyAssociationAttachments(raw,candidates,checked)){raw.put("status","ERROR");raw.put("message","No se pudo distinguir la unión discontinua de la clase de asociación. Acerca la foto a esa unión y vuelve a analizar.");}
    }

    @SuppressWarnings("unchecked")
    List<Map<String,Object>> associationAttachmentCandidates(Map<String,Object> raw) {
        List<Map<String,Object>> candidates=new ArrayList<>();
        if(!(raw.get("relations") instanceof List<?> rows)||!(raw.get("classes") instanceof List<?> classes))return candidates;
        for(Object value:rows)if(value instanceof Map<?,?> edge&&"association".equals(edge.get("type"))&&text(edge.get("sourceMultiplicity")).endsWith("*")&&text(edge.get("targetMultiplicity")).endsWith("*")&&text(edge.get("associationClassName")).isBlank()) {
            String a=text(edge.get("sourceClassName")),b=text(edge.get("targetClassName"));
            for(Object c:classes)if(c instanceof Map<?,?> clazz){String name=text(clazz.get("name"));if(name.equals(a)||name.equals(b))continue;
                candidates.add(Map.of("index",candidates.size(),"A",a,"B",b,"C",name));
            }
        }
        return candidates;
    }

    private boolean isAssociationBetween(Map<?,?> edge,String a,String b){return "association".equals(edge.get("type"))&&((a.equals(edge.get("sourceClassName"))&&b.equals(edge.get("targetClassName")))||(b.equals(edge.get("sourceClassName"))&&a.equals(edge.get("targetClassName"))));}

    @SuppressWarnings("unchecked")
    boolean applyAssociationAttachments(Map<String,Object> raw,List<Map<String,Object>> candidates,Map<String,Object> checked) {
        if(!(checked.get("attachments") instanceof List<?> rows)||rows.size()!=candidates.size())return false;
        var updated=new ArrayList<Map<String,Object>>();for(Object e:(List<?>)raw.get("relations"))updated.add(new LinkedHashMap<>((Map<String,Object>)e));
        Set<Integer> seen=new java.util.HashSet<>();
        for(Object value:rows){if(!(value instanceof List<?> fields)||fields.size()!=4||!(fields.get(0) instanceof Number n)||!(fields.get(2) instanceof Boolean ca)||!(fields.get(3) instanceof Boolean cb))return false;
            int index=n.intValue();if(index<0||index>=candidates.size()||n.doubleValue()!=index||!seen.add(index))return false;
            String verdict=text(fields.get(1));if(!Set.of("CENTER_DASHED","BINARY_LINES").contains(verdict))return false;
            if("BINARY_LINES".equals(verdict))continue;
            var candidate=candidates.get(index);String a=text(candidate.get("A")),b=text(candidate.get("B")),c=text(candidate.get("C"));
            var matches=updated.stream().filter(e->isAssociationBetween(e,a,b)).toList();if(matches.size()!=1)return false;
            if(!text(matches.get(0).get("associationClassName")).isBlank()&&!c.equals(matches.get(0).get("associationClassName")))return false;
            matches.get(0).put("associationClassName",c);
            updated.removeIf(e->(!ca&&isAssociationBetween(e,c,a))||(!cb&&isAssociationBetween(e,c,b)));
        }
        raw.put("relations",updated);return true;
    }

    @SuppressWarnings("unchecked")
    boolean reconcileReviewedGraph(Map<String,Object> raw,Map<String,Object> checked) {
        if(!"READY".equals(checked.get("status"))||!(raw.get("classes") instanceof List<?> original)||!(checked.get("relations") instanceof List<?> rows))return false;
        List<Map<String,Object>> classes=new ArrayList<>();
        for(Object value:original){if(!(value instanceof Map<?,?>))return false;classes.add(new LinkedHashMap<>((Map<String,Object>)value));}
        Map<String,String> resolved=new LinkedHashMap<>(),renamed=new LinkedHashMap<>();
        for(Object row:rows){
            if(!(row instanceof List<?> fields)||fields.size()!=7)return false;
            for(int index:List.of(0,1,6)){
                String name=text(fields.get(index));if(name.isBlank()){if(index<2)return false;continue;}
                if(resolved.containsKey(normalize(name)))continue;
                Map<String,Object> match=findUnique(classes,name);
                if(match==null){
                    var candidates=classes.stream().filter(c->isMinorTitleCorrection(text(c.get("name")),name)).toList();
                    if(candidates.size()!=1)return false;match=candidates.get(0);
                    String old=text(match.get("name"));if(renamed.containsKey(old)&&!normalize(renamed.get(old)).equals(normalize(name)))return false;
                    renamed.put(old,name);
                }
                resolved.put(normalize(name),text(match.get("name")));
            }
        }
        Set<String> unique=new java.util.HashSet<>();
        for(var c:classes){String old=text(c.get("name")),name=renamed.getOrDefault(old,old);if(!unique.add(normalize(name)))return false;c.put("name",name);}
        List<Map<String,Object>> relations=new ArrayList<>();
        for(Object row:rows){
            var fields=(List<?>)row;String type=text(fields.get(2));if(!RELATION_TYPES.contains(type))return false;
            var relation=new LinkedHashMap<String,Object>();
            for(int index:List.of(0,1,6)){
                String name=text(fields.get(index)),old=resolved.get(normalize(name)),canonical=old==null?"":renamed.getOrDefault(old,old);
                relation.put(index==0?"sourceClassName":index==1?"targetClassName":"associationClassName",canonical);
            }
            relation.put("type",type);relation.put("label",cleanText(fields.get(3),120));
            for(int index:List.of(4,5)){
                String multiplicity=text(fields.get(index));if(!multiplicity.isBlank()&&!multiplicity.matches(MULTIPLICITY))return false;
                String endpoint=index==4?"sourceMultiplicity":"targetMultiplicity";
                if(!multiplicity.isBlank()&&raw.get("relations") instanceof List<?> previous){
                    for(Object old:previous)if(old instanceof Map<?,?> prior&&type.equals(prior.get("type"))){
                        boolean same=normalize(text(prior.get("sourceClassName"))).equals(normalize(resolved.get(normalize(text(fields.get(0))))))&&normalize(text(prior.get("targetClassName"))).equals(normalize(resolved.get(normalize(text(fields.get(1))))));
                        boolean reverse=normalize(text(prior.get("sourceClassName"))).equals(normalize(resolved.get(normalize(text(fields.get(1))))))&&normalize(text(prior.get("targetClassName"))).equals(normalize(resolved.get(normalize(text(fields.get(0))))));
                        String priorEndpoint=reverse?(index==4?"targetMultiplicity":"sourceMultiplicity"):endpoint;
                        if((same||reverse)&&prior.containsKey(priorEndpoint)&&text(prior.get(priorEndpoint)).isBlank())multiplicity="";
                    }
                }
                relation.put(endpoint,multiplicity);
            }
            if(!text(relation.get("associationClassName")).isBlank()&&(relation.get("associationClassName").equals(relation.get("sourceClassName"))||relation.get("associationClassName").equals(relation.get("targetClassName"))))return false;
            relations.add(relation);
        }
        raw.put("classes",classes);raw.put("relations",relations);return true;
    }

    @SuppressWarnings("unchecked")
    private void reviewFinalTitleStrokes(Map<String,Object> raw,MultipartFile image) {
        if(!(raw.get("classes") instanceof List<?> classes)||classes.isEmpty()||classes.size()>12)return;
        try {
            var original=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.getBytes()));if(original==null)return;
            int width=512,rowHeight=128;var sheet=new java.awt.image.BufferedImage(width,rowHeight*classes.size(),java.awt.image.BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,width,sheet.getHeight());g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,20));
                for(int i=0;i<classes.size();i++){
                    if(!(classes.get(i) instanceof Map<?,?> c)||!(c.get("x") instanceof Number nx)||!(c.get("y") instanceof Number ny))return;
                    double x=nx.doubleValue(),y=ny.doubleValue();if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>=1||y<0||y>=1)return;
                    int left=Math.max(0,(int)((x-.02)*original.getWidth())),top=Math.max(0,(int)((y-.025)*original.getHeight()));
                    int w=Math.min(original.getWidth()-left,Math.max(1,(int)(original.getWidth()*.34))),h=Math.min(original.getHeight()-top,Math.max(1,(int)(original.getHeight()*.13)));
                    double scale=Math.min((width-20.0)/w,(rowHeight-35.0)/h);g.setColor(java.awt.Color.BLACK);g.drawString("#"+i,5,i*rowHeight+23);g.drawImage(original.getSubimage(left,top,w,h),10,i*rowHeight+30,(int)(w*scale),(int)(h*scale),null);
                }
            } finally {g.dispose();}
            var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(sheet,"jpeg",bytes);
            Map<String,Object> item=Map.of("type","array","prefixItems",List.of(Map.of("type","integer"),Map.of("type",List.of("string","null"))),"minItems",2,"maxItems",2);
            Map<String,Object> schema=Map.of("type","object","properties",Map.of("titles",Map.of("type","array","items",item)),"required",List.of("titles"));
            String prompt="Transcribe SOLO el título manuscrito de cada franja numerada, incluidas letras finales. No traduzcas ni inventes puntuación: distingue l de ! y del borde del recuadro. Si está cortado o no se lee usa null. JSON compacto titles=[[índice,título literal]].";
            Map<String,Object> checked=mapper.readValue(providerPipeline.reviewImageGraph(prompt,schema,"data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()),32+classes.size()*16),new TypeReference<>() {});
            reconcileFinalTitleStrokes(raw,checked);
        } catch(Exception error){
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            var warnings=new ArrayList<String>();if(raw.get("warnings") instanceof List<?> list)list.forEach(v->warnings.add(String.valueOf(v)));
            warnings.add("No se pudo confirmar alguna letra final de los títulos; revisa los nombres manuscritos.");raw.put("warnings",warnings);
        }
    }

    @SuppressWarnings("unchecked")
    void reconcileFinalTitleStrokes(Map<String,Object> raw,Map<String,Object> checked) {
        if(!(raw.get("classes") instanceof List<?> classes))return;
    if(!(checked.get("titles") instanceof List<?> rows))return;
    List<Map<String,Object>> titles=new ArrayList<>();
    for(Object value:rows)if(value instanceof List<?> fields&&fields.size()==2&&fields.get(0) instanceof Number index&&index.doubleValue()==index.intValue()&&index.intValue()>=0&&index.intValue()<classes.size()){
        var c=(Map<String,Object>)classes.get(index.intValue());String old=text(c.get("name")),candidate=text(fields.get(1));String a=normalize(old),b=normalize(candidate);
        boolean finalStroke=b.startsWith(a)&&b.length()==a.length()+1&&Set.of('l','i').contains(b.charAt(b.length()-1));
        titles.add(Map.of("index",index.intValue(),"name",finalStroke?candidate:old));
    }
    reconcileImageTitles(raw,Map.of("titles",titles));
    }

    @SuppressWarnings("unchecked")
    void reconcileImageTitles(Map<String,Object> raw,Map<String,Object> checked) {
        if(!(raw.get("classes") instanceof List<?> classes)||!(checked.get("titles") instanceof List<?> titles))return;
        Map<Integer,String> replacements=new LinkedHashMap<>();Set<Integer> seen=new java.util.HashSet<>();
        for(Object value:titles) {
            if(!(value instanceof Map<?,?> title)||!(title.get("index") instanceof Number number))return;
            int index=number.intValue();if(number.doubleValue()!=index||index<0||index>=classes.size()||!seen.add(index))return;
            String name=validName(title.get("name"));if(name!=null)replacements.put(index,name);
        }
        if(seen.size()!=classes.size())return;
        Map<String,String> names=new LinkedHashMap<>();Set<String> unique=new java.util.HashSet<>();
        for(int i=0;i<classes.size();i++) {
            if(!(classes.get(i) instanceof Map<?,?> c))return;
            String old=text(c.get("name")),name=replacements.getOrDefault(i,old);
            if(!isMinorTitleCorrection(old,name)||(!normalize(old).equals(normalize(name))&&findUnique((List<Map<String,Object>>)classes,name)!=null)){name=old;replacements.remove(i);}
            if(!unique.add(name.toLowerCase(Locale.ROOT)))return;names.put(old,name);
        }
        List<Map<String,Object>> renamed=new ArrayList<>();
        for(Object value:classes){var c=new LinkedHashMap<>((Map<String,Object>)value);c.put("name",names.get(text(c.get("name"))));renamed.add(c);}
        List<Map<String,Object>> relations=new ArrayList<>();
        if(raw.get("relations") instanceof List<?> list)for(Object value:list) {
            if(!(value instanceof Map<?,?>))continue;
            var relation=new LinkedHashMap<>((Map<String,Object>)value);
            for(String key:List.of("sourceClassName","targetClassName","associationClassName")) {
                String old=text(relation.get(key));if(names.containsKey(old))relation.put(key,names.get(old));
            }
            relations.add(relation);
        }
        raw.put("classes",renamed);raw.put("relations",relations);
        if(replacements.size()<classes.size()) {
            var warnings=new ArrayList<String>();if(raw.get("warnings") instanceof List<?> old)old.forEach(w->warnings.add(String.valueOf(w)));
            warnings.add("Algún título no se lee completo en la revisión ampliada; revisa los nombres antes de insertar el diagrama.");raw.put("warnings",warnings);
        }
    }

    private boolean isMinorTitleCorrection(String original,String candidate) {
        String a=normalize(original).replaceAll("[\\s_-]",""),b=normalize(candidate).replaceAll("[\\s_-]","");
        if(a.equals(b))return true;
        if(Math.min(a.length(),b.length())<4)return false;
        int limit=Math.min(2,Math.max(a.length(),b.length())/6);
        if(Math.abs(a.length()-b.length())>limit)return false;
        int[] previous=new int[b.length()+1];for(int j=0;j<previous.length;j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){
            int[] current=new int[b.length()+1];current[0]=i;
            for(int j=1;j<=b.length();j++)current[j]=Math.min(Math.min(previous[j]+1,current[j-1]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            previous=current;
        }
        return previous[b.length()]<=limit;
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
        if(raw.get("relations") instanceof List<?> rawRelations) for(Object item:rawRelations){if(!(item instanceof Map<?,?> any))continue;Map<String,Object> relation=(Map<String,Object>)any;Map<String,Object> source=findUnique(classes,text(relation.get("sourceClassName"))),target=findUnique(classes,text(relation.get("targetClassName")));Map<String,Object> clean=new LinkedHashMap<>();clean.put("sourceClassName",source==null?"":source.get("name"));clean.put("targetClassName",target==null?"":target.get("name"));clean.put("type",text(relation.get("type")));clean.put("label",cleanText(relation.get("label"),120));clean.put("sourceMultiplicity",imageMultiplicity(relation.get("sourceMultiplicity"),warnings));clean.put("targetMultiplicity",imageMultiplicity(relation.get("targetMultiplicity"),warnings));if(source==null||target==null||!validImageRelation(clean)){warnings.add("Se descartó una relación con extremos o multiplicidades poco claros.");continue;}String associationClassName=text(relation.get("associationClassName"));Map<String,Object> associationClass=associationClassName.isBlank()?null:findUnique(classes,associationClassName);clean.put("associationClassName",associationClass==null?"":associationClass.get("name"));relations.add(clean);}
        Map<String,Object> result=new LinkedHashMap<>();result.put("status","READY");result.put("classes",classes);result.put("relations",relations);result.put("warnings",warnings);result.put("message",message.isBlank()?"Imagen analizada. Revisa el resultado antes de insertarlo.":message);result.put("provider","OPENAI");result.put("model",visionModel());return result;
    }

    private boolean validImageRelation(Map<String,Object> relation) {
        return RELATION_TYPES.contains(text(relation.get("type")))&&List.of("sourceMultiplicity","targetMultiplicity").stream().allMatch(k->text(relation.get(k)).isBlank()||text(relation.get(k)).matches(MULTIPLICITY));
    }

    private String imageMultiplicity(Object value,List<String> warnings){
        String multiplicity=text(value).replaceAll("\\s+", "").replaceAll("\\.{3,}","..");
        if(multiplicity.isBlank()){warnings.add("Una multiplicidad no está indicada o no se lee en la imagen; se conserva vacía sin inventar un número.");return "";}
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
