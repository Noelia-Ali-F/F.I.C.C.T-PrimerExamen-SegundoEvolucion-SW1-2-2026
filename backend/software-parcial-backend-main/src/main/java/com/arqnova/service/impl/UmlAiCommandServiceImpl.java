package com.arqnova.service.impl;

import com.arqnova.dto.request.UmlAiCommandRequest;
import com.arqnova.dto.response.UmlAiCommandResponse;
import com.arqnova.dto.response.UmlAiOperationResponse;
import com.arqnova.service.GeminiUmlInterpreter;
import com.arqnova.service.UmlAiCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UmlAiCommandServiceImpl implements UmlAiCommandService {
    private static final String NAME = "([\\p{L}][\\p{L}\\p{N}_-]*)";
    private GeminiUmlInterpreter gemini;

    @Autowired(required = false)
    void setGemini(GeminiUmlInterpreter gemini) { this.gemini = gemini; }

    @Override
    public UmlAiCommandResponse interpret(UmlAiCommandRequest request) {
        if (gemini != null && gemini.isConfigured()) {
            try {
                return gemini.interpret(request);
            } catch (Exception exception) {
                log.warn("Gemini UML no disponible; se usa el parser seguro de respaldo: {}", exception.getMessage());
            }
        }
        UmlAiCommandResponse fallback = interpretDeterministic(request);
        fallback.setInterpreter("DETERMINISTIC_FALLBACK");
        fallback.setFallbackUsed(true);
        if (fallback.getOperation() != null) {
            String elementType = fallback.getOperation().startsWith("ATTRIBUTE_") ? "attribute"
                    : fallback.getOperation().startsWith("METHOD_") ? "method"
                    : fallback.getOperation().startsWith("RELATION_") ? "relation" : "class";
            fallback.setOperations(List.of(UmlAiOperationResponse.builder().type(fallback.getOperation())
                    .targetId(fallback.getTargetId()).elementType(elementType)
                    .target(Collections.emptyMap()).parameters(fallback.getParameters()).build()));
        } else fallback.setOperations(Collections.emptyList());
        return fallback;
    }

    private UmlAiCommandResponse interpretDeterministic(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        ModelIndex model = new ModelIndex(request.getModel());

        Matcher matcher;
        matcher = match(text, "(?:(?:quiero )?(?:crear|crea|agregar|agrega|añadir|anadir|añade|anade)(?: una| la)?|nueva) clase " + NAME + "(?: con (.+))?");
        if (matcher.matches()) {
            String name = matcher.group(1);
            if (!model.classesNamed(name).isEmpty()) return error("Ya existe una clase llamada " + name + ".");
            List<Map<String, Object>> attributes = parseAttributes(matcher.group(2));
            if (matcher.group(2) != null && attributes.isEmpty()) return clarification("No pude identificar los atributos. Usa, por ejemplo: “Crea Venta con id Long y fecha Date”.");
            return ready("CLASS_CREATE", null, params("name", name, "attributes", attributes), "Crearé la clase " + name + ".");
        }

        matcher = match(text, "(?:cambia|renombra|modifica) (?:el )?nombre de (?:la clase )?" + NAME + " (?:a|por) " + NAME);
        if (matcher.matches()) return renameClass(model, matcher.group(1), matcher.group(2));

        matcher = match(text, "(?:agrega|añade|anade|crea|ponle)(?: a " + NAME + ")? (?:un |el )?(?:atributo|campo) " + NAME + "(?: de tipo | tipo |:\\s*| )" + NAME + "(?: como (pk|clave primaria|identificador(?: principal)?|primary key))?(?:(?: a| en| de) " + NAME + ")?");
        if (matcher.matches()) {
            String className = firstNonBlank(matcher.group(1), matcher.group(5));
            if (className == null) return clarification("Indica en qué clase debo agregar el atributo.");
            Lookup clazz = model.classNamed(className); if (!clazz.ok()) return clazz.response();
            if (model.attributeNamed(clazz.value(), matcher.group(2)).found()) return error("La clase " + className + " ya tiene el atributo " + matcher.group(2) + ".");
            return ready("ATTRIBUTE_CREATE", null, params("classId", clazz.id(), "className", className, "name", matcher.group(2), "type", matcher.group(3), "isPrimaryKey", matcher.group(4) != null), "Agregaré " + matcher.group(2) + ": " + matcher.group(3) + " a " + className + ".");
        }

        matcher = match(text, "(?:agrega|añade|anade|crea) " + NAME + "(?: de tipo | tipo |:\\s*| )" + NAME + "(?: como (pk|clave primaria|identificador(?: principal)?|primary key))? (?:a|en) " + NAME);
        if (matcher.matches()) return createAttribute(model, matcher.group(4), matcher.group(1), matcher.group(2), matcher.group(3) != null);

        matcher = match(text, "(?:añade|anade) " + NAME + " " + NAME + " a " + NAME);
        if (matcher.matches()) return createAttribute(model, matcher.group(3), matcher.group(1), matcher.group(2), false);

        matcher = match(text, NAME + " debe tener (?:un )?(?:atributo|campo) " + NAME + "(?: de tipo| tipo|:) ?" + NAME);
        if (matcher.matches()) return createAttribute(model, matcher.group(1), matcher.group(2), matcher.group(3), false);

        matcher = match(text, "(?:marca|convierte) " + NAME + " (?:como|en) (?:pk|clave primaria|identificador(?: principal)?|primary key) (?:en|de) " + NAME);
        if (matcher.matches()) return updatePrimaryKey(model, matcher.group(2), matcher.group(1), true);

        matcher = match(text, "(?:quita la pk del atributo|desmarca) " + NAME + " (?:como identificador )?(?:en|de) " + NAME);
        if (matcher.matches()) return updatePrimaryKey(model, matcher.group(2), matcher.group(1), false);

        matcher = match(text, "(?:elimina|eliminar|borra|quita) (?:el )?(?:atributo )?" + NAME + " (?:de|en) " + NAME);
        if (matcher.matches()) {
            Lookup clazz = model.classNamed(matcher.group(2)); if (!clazz.ok()) return clazz.response();
            Lookup attribute = model.attributeNamed(clazz.value(), matcher.group(1)); if (!attribute.ok()) return attribute.response();
            return ready("ATTRIBUTE_DELETE", attribute.id(), params("classId", clazz.id(), "className", matcher.group(2), "attributeName", matcher.group(1)), "Eliminaré el atributo " + matcher.group(1) + " de " + matcher.group(2) + ".");
        }

        matcher = match(text, "(?:cambia|renombra) el atributo " + NAME + " de " + NAME + " (?:a|por) " + NAME);
        if (matcher.matches()) return updateAttribute(model, matcher.group(2), matcher.group(1), "name", matcher.group(3));

        matcher = match(text, "(?:cambia )?(?:el tipo de )?" + NAME + " (?:de " + NAME + " )?(?:de " + NAME + " )?a " + NAME + "(?: en " + NAME + ")?");
        if (matcher.matches()) {
            String className = firstNonBlank(matcher.group(5), matcher.group(2));
            if (className != null) return updateAttribute(model, className, matcher.group(1), "type", matcher.group(4));
        }

        matcher = match(text, "el atributo " + NAME + " de " + NAME + " ahora es " + NAME);
        if (matcher.matches()) return updateAttribute(model, matcher.group(2), matcher.group(1), "type", matcher.group(3));

        matcher = match(text, "(?:agrega|añade|anade|crea|ponle)(?: a " + NAME + ")? (?:el |un )?m[eé]todo " + NAME + "(?:(?: a| en) " + NAME + ")?(?: con par[aá]metros (.+?))?(?: (?:y )?(?:con )?retorno " + NAME + ")?");
        if (matcher.matches()) {
            String className = firstNonBlank(matcher.group(1), matcher.group(3));
            String returnType = firstNonBlank(matcher.group(5), "void");
            return createMethod(model, className, matcher.group(2), parseParameters(matcher.group(4)), returnType);
        }

        matcher = match(text, "(?:agrega|añade|anade|crea) (?:en )?" + NAME + " (?:el m[eé]todo )?" + NAME + "(?: con par[aá]metro?s? (.+?))?(?: (?:y )?(?:con )?retorno " + NAME + ")?");
        if (matcher.matches()) return createMethod(model, matcher.group(1), matcher.group(2), parseParameters(matcher.group(3)), firstNonBlank(matcher.group(4), "void"));

        matcher = match(text, "(?:agrega|añade|anade|crea) " + NAME + " a " + NAME + "(?: con retorno " + NAME + ")?");
        if (matcher.matches()) return createMethod(model, matcher.group(2), matcher.group(1), "", firstNonBlank(matcher.group(3), "void"));

        matcher = match(text, "crea " + NAME + " en " + NAME + " que retorne " + NAME);
        if (matcher.matches()) return createMethod(model, matcher.group(2), matcher.group(1), "", matcher.group(3));

        matcher = match(text, NAME + " debe tener (?:un )?m[eé]todo " + NAME);
        if (matcher.matches()) return createMethod(model, matcher.group(1), matcher.group(2), "", "void");

        matcher = match(text, "el m[eé]todo " + NAME + " de " + NAME + " devuelve " + NAME);
        if (matcher.matches()) return updateMethod(model, matcher.group(2), matcher.group(1), "returnType", matcher.group(3));

        matcher = match(text, "(?:renombra|cambia) (?!nombre\\b)" + NAME + " (?:de " + NAME + " a|por) " + NAME + "(?: en " + NAME + ")?");
        if (matcher.matches()) return updateMethod(model, firstNonBlank(matcher.group(2), matcher.group(4)), matcher.group(1), "name", matcher.group(3));

        matcher = match(text, "cambia (?:el )?retorno de " + NAME + " en " + NAME + " a " + NAME);
        if (matcher.matches()) return updateMethod(model, matcher.group(2), matcher.group(1), "returnType", matcher.group(3));

        matcher = match(text, "cambia (?:los )?par[aá]metros de " + NAME + " en " + NAME + " a (.+)");
        if (matcher.matches()) return updateMethod(model, matcher.group(2), matcher.group(1), "parameters", parseParameters(matcher.group(3)));

        matcher = match(text, "(?:elimina|borra|quita) (?:el m[eé]todo )?" + NAME + " (?:de|en) " + NAME);
        if (matcher.matches()) {
            Lookup clazz = model.classNamed(matcher.group(2)); if (!clazz.ok()) return clazz.response();
            Lookup method = model.methodNamed(clazz.value(), matcher.group(1)); if (!method.ok()) return method.response();
            return ready("METHOD_DELETE", method.id(), params("classId", clazz.id()), "Eliminaré el método " + matcher.group(1) + ".");
        }

        matcher = match(text, "(?:cambia|edita|modifica) (?:el )?(nombre|tipo) (?:del )?atributo " + NAME + " (?:de|en) " + NAME + " (?:a|por) " + NAME);
        if (matcher.matches()) {
            Lookup clazz = model.classNamed(matcher.group(3)); if (!clazz.ok()) return clazz.response();
            Lookup attribute = model.attributeNamed(clazz.value(), matcher.group(2)); if (!attribute.ok()) return attribute.response();
            String field = normalize(matcher.group(1)).equals("tipo") ? "type" : "name";
            return ready("ATTRIBUTE_UPDATE", attribute.id(), params("classId", clazz.id(), field, matcher.group(4)), "Actualizaré el " + matcher.group(1) + " del atributo " + matcher.group(2) + ".");
        }

        matcher = match(text, "(?:relaciona|asocia|conecta|une|crea (?:una )?relación entre|crea (?:una )?relacion entre) " + NAME + " (?:con|y) " + NAME + "(?: mediante " + NAME + ")?(?: de (.+))?");
        if (matcher.matches()) {
            Lookup source = model.classNamed(matcher.group(1)); if (!source.ok()) return source.response();
            Lookup target = model.classNamed(matcher.group(2)); if (!target.ok()) return target.response();
            String type = matcher.group(3) == null ? "association" : relationType(matcher.group(3));
            if (type == null) return clarification("Usa un tipo de relación válido: asociación, agregación, composición, herencia o dependencia.");
            String[] multiplicities = multiplicities(matcher.group(4));
            return ready("RELATION_CREATE", null, params("sourceId", source.id(), "targetId", target.id(), "sourceName", matcher.group(1), "targetName", matcher.group(2), "sourceMultiplicity", multiplicities[0], "targetMultiplicity", multiplicities[1], "type", type), "Crearé la relación entre " + matcher.group(1) + " y " + matcher.group(2) + ".");
        }

        matcher = match(text, NAME + " se relaciona con " + NAME);
        if (matcher.matches()) return createRelation(model, matcher.group(1), matcher.group(2), "association", null);

        matcher = match(text, "haz que " + NAME + " herede de " + NAME);
        if (matcher.matches()) return createRelation(model, matcher.group(1), matcher.group(2), "inheritance", null);

        matcher = match(text, "crea una dependencia de " + NAME + " hacia " + NAME);
        if (matcher.matches()) return createRelation(model, matcher.group(1), matcher.group(2), "dependency", null);

        matcher = match(text, "(?:un )?" + NAME + " (?:puede tener|tiene) muchos " + NAME);
        if (matcher.matches()) return createRelationNatural(model, matcher.group(1), matcher.group(2), "1", "0..*");

        matcher = match(text, NAME + " pertenece a un " + NAME);
        if (matcher.matches()) return createRelationNatural(model, matcher.group(1), matcher.group(2), "0..*", "1");

        matcher = match(text, "relaciona " + NAME + " (\\*|\\d+(?:\\.\\.(?:\\d+|\\*))?) con " + NAME + " (\\*|\\d+(?:\\.\\.(?:\\d+|\\*))?)");
        if (matcher.matches()) return createRelationNatural(model, matcher.group(1), matcher.group(3), matcher.group(2), matcher.group(4));

        matcher = match(text, "crea una (asociación|asociacion) " + NAME + " (uno|1|cero o uno|muchos|uno o muchos) a " + NAME + " (uno|1|cero o uno|muchos|uno o muchos)");
        if (matcher.matches()) return createRelationNatural(model, matcher.group(2), matcher.group(4), multiplicityValue(matcher.group(3)), multiplicityValue(matcher.group(5)));

        matcher = match(text, "(?:cambia|modifica) (?:la )?multiplicidad (?:de la relación |de la relacion |entre |de )?" + NAME + " (?:con|y) " + NAME + " (?:a|de) (.+)");
        if (matcher.matches()) {
            Lookup source = model.classNamed(matcher.group(1)); if (!source.ok()) return source.response();
            Lookup target = model.classNamed(matcher.group(2)); if (!target.ok()) return target.response();
            Lookup relation = model.relationBetween(source.id(), target.id()); if (!relation.ok()) return relation.response();
            String[] multiplicities = multiplicities(matcher.group(3));
            return ready("RELATION_UPDATE", relation.id(), params("sourceMultiplicity", multiplicities[0], "targetMultiplicity", multiplicities[1]), "Actualizaré las multiplicidades de la relación.");
        }

        matcher = match(text, "(?:cambia|modifica) (?:el )?tipo (?:de la )?relación entre " + NAME + " (?:con|y) " + NAME + " (?:a|por) " + NAME);
        if (matcher.matches()) {
            Lookup source = model.classNamed(matcher.group(1)); if (!source.ok()) return source.response();
            Lookup target = model.classNamed(matcher.group(2)); if (!target.ok()) return target.response();
            Lookup relation = model.relationBetween(source.id(), target.id()); if (!relation.ok()) return relation.response();
            String type = relationType(matcher.group(3));
            if (type == null) return clarification("Usa un tipo de relación válido: asociación, agregación, composición, herencia o dependencia.");
            return ready("RELATION_UPDATE", relation.id(), params("type", type), "Actualizaré el tipo de la relación a " + matcher.group(3) + ".");
        }

        matcher = match(text, "(?:cambia|modifica) (?:la )?relación " + NAME + " " + NAME + " (?:a|por) " + NAME);
        if (matcher.matches()) {
            Lookup source = model.classNamed(matcher.group(1)); if (!source.ok()) return source.response();
            Lookup target = model.classNamed(matcher.group(2)); if (!target.ok()) return target.response();
            Lookup relation = model.relationBetween(source.id(), target.id()); if (!relation.ok()) return relation.response();
            String type = relationType(matcher.group(3));
            if (type == null) return clarification("Usa un tipo de relación válido: asociación, agregación, composición, herencia o dependencia.");
            return ready("RELATION_UPDATE", relation.id(), params("type", type), "Actualizaré el tipo de la relación.");
        }

        matcher = match(text, "(?:cambia|modifica) (?:la )?(descripción|descripcion|nombre) (?:de la )?relación entre " + NAME + " (?:con|y) " + NAME + " (?:a|por) (.+)");
        if (matcher.matches()) {
            Lookup source = model.classNamed(matcher.group(2)); if (!source.ok()) return source.response();
            Lookup target = model.classNamed(matcher.group(3)); if (!target.ok()) return target.response();
            Lookup relation = model.relationBetween(source.id(), target.id()); if (!relation.ok()) return relation.response();
            return ready("RELATION_UPDATE", relation.id(), params("label", matcher.group(4).trim()), "Actualizaré la descripción de la relación.");
        }

        matcher = match(text, "(?:elimina|eliminar|borra|quita) (?:la )?(?:relación|relacion|asociación|asociacion) (?:entre )?" + NAME + " (?:con|y) " + NAME);
        if (matcher.matches()) {
            Lookup source = model.classNamed(matcher.group(1)); if (!source.ok()) return source.response();
            Lookup target = model.classNamed(matcher.group(2)); if (!target.ok()) return target.response();
            Lookup relation = model.relationBetween(source.id(), target.id()); if (!relation.ok()) return relation.response();
            return ready("RELATION_DELETE", relation.id(), Collections.emptyMap(), "Eliminaré la relación entre " + matcher.group(1) + " y " + matcher.group(2) + ".");
        }

        matcher = match(text, "(?:renombra|cambia) " + NAME + " (?:a|por) " + NAME);
        if (matcher.matches()) return renameClass(model, matcher.group(1), matcher.group(2));

        matcher = match(text, NAME + " ahora se llama " + NAME);
        if (matcher.matches()) return renameClass(model, matcher.group(1), matcher.group(2));

        matcher = match(text, "(?:elimina|eliminar|borra|quita) (?:la )?(?:clase )?" + NAME);
        if (matcher.matches()) {
            Lookup clazz = model.classNamed(matcher.group(1)); if (!clazz.ok()) return clazz.response();
            return ready("CLASS_DELETE", clazz.id(), Collections.emptyMap(), "Eliminaré la clase " + matcher.group(1) + " y sus relaciones asociadas.");
        }

        return clarification("No pude interpretar la instrucción sin riesgo de modificar algo incorrecto. Reformúlala indicando la operación, el elemento y sus datos.");
    }

    private Matcher match(String text, String regex) { return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text); }

    private String cleanInstruction(String value) {
        return value.trim().replaceAll("[.!?¡¿;]+$", "").replaceAll("\\s*;\\s*", ", ").replaceAll("\\s+", " ");
    }

    private UmlAiCommandResponse createAttribute(ModelIndex model, String className, String name, String type, boolean primaryKey) {
        Lookup clazz = model.classNamed(className); if (!clazz.ok()) return clazz.response();
        if (model.attributeNamed(clazz.value(), name).found()) return error("La clase " + className + " ya tiene el atributo " + name + ".");
        return ready("ATTRIBUTE_CREATE", null, params("classId", clazz.id(), "className", className,
                "name", name, "type", type, "isPrimaryKey", primaryKey), "Agregaré " + name + ": " + type + " a " + className + ".");
    }

    private UmlAiCommandResponse updateAttribute(ModelIndex model, String className, String attributeName, String field, Object value) {
        Lookup clazz = model.classNamed(className); if (!clazz.ok()) return clazz.response();
        Lookup attribute = model.attributeNamed(clazz.value(), attributeName); if (!attribute.ok()) return attribute.response();
        return ready("ATTRIBUTE_UPDATE", attribute.id(), params("classId", clazz.id(), field, value), "Actualizaré el atributo " + attributeName + ".");
    }

    private UmlAiCommandResponse updatePrimaryKey(ModelIndex model, String className, String attributeName, boolean value) {
        return updateAttribute(model, className, attributeName, "isPrimaryKey", value);
    }

    private UmlAiCommandResponse createMethod(ModelIndex model, String className, String name, String parameters, String returnType) {
        if (className == null) return clarification("Indica en qué clase debo agregar el método.");
        Lookup clazz = model.classNamed(className); if (!clazz.ok()) return clazz.response();
        if (model.methodNamed(clazz.value(), name).found()) return error("La clase " + className + " ya tiene el método " + name + ".");
        return ready("METHOD_CREATE", null, params("classId", clazz.id(), "name", name,
                "parameters", parameters, "returnType", returnType), "Agregaré el método " + name + " a " + className + ".");
    }

    private UmlAiCommandResponse updateMethod(ModelIndex model, String className, String methodName, String field, String value) {
        if (className == null) return clarification("Indica la clase que contiene el método.");
        Lookup clazz = model.classNamed(className); if (!clazz.ok()) return clazz.response();
        Lookup method = model.methodNamed(clazz.value(), methodName); if (!method.ok()) return method.response();
        return ready("METHOD_UPDATE", method.id(), params("classId", clazz.id(), field, value), "Actualizaré el método " + methodName + ".");
    }

    private String parseParameters(String value) {
        if (value == null || value.isBlank()) return "";
        List<String> parameters = new ArrayList<>();
        for (String part : value.split("(?i)\\s+y\\s+|\\s*,\\s*")) {
            Matcher matcher = Pattern.compile("^" + NAME + "(?: de tipo | tipo |:\\s*| )" + NAME + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(part.trim());
            if (matcher.matches()) parameters.add(matcher.group(1) + ": " + matcher.group(2));
        }
        return String.join(", ", parameters);
    }

    private UmlAiCommandResponse createRelation(ModelIndex model, String sourceName, String targetName, String type, String multiplicity) {
        Lookup source = model.classNamed(sourceName); if (!source.ok()) return source.response();
        Lookup target = model.classNamed(targetName); if (!target.ok()) return target.response();
        String[] values = multiplicities(multiplicity);
        return ready("RELATION_CREATE", null, params("sourceId", source.id(), "targetId", target.id(),
                "sourceName", sourceName, "targetName", targetName, "sourceMultiplicity", values[0],
                "targetMultiplicity", values[1], "type", type), "Crearé la relación entre " + sourceName + " y " + targetName + ".");
    }

    private UmlAiCommandResponse createRelationNatural(ModelIndex model, String sourceName, String targetName, String sourceMultiplicity, String targetMultiplicity) {
        Lookup source = model.classNamedNatural(sourceName); if (!source.ok()) return source.response();
        Lookup target = model.classNamedNatural(targetName); if (!target.ok()) return target.response();
        return ready("RELATION_CREATE", null, params("sourceId", source.id(), "targetId", target.id(),
                "sourceName", source.value().get("name"), "targetName", target.value().get("name"),
                "sourceMultiplicity", sourceMultiplicity, "targetMultiplicity", targetMultiplicity, "type", "association"),
                "Crearé la relación entre " + source.value().get("name") + " y " + target.value().get("name") + ".");
    }

    private String multiplicityValue(String value) {
        return switch (normalize(value)) {
            case "uno", "1" -> "1";
            case "cero o uno" -> "0..1";
            case "muchos" -> "0..*";
            case "uno o muchos" -> "1..*";
            default -> value;
        };
    }

    private UmlAiCommandResponse renameClass(ModelIndex model, String oldName, String newName) {
        Lookup clazz = model.classNamed(oldName); if (!clazz.ok()) return clazz.response();
        if (!model.classesNamed(newName).isEmpty()) return error("Ya existe una clase llamada " + newName + ".");
        return ready("CLASS_UPDATE", clazz.id(), params("name", newName), "Renombraré " + oldName + " a " + newName + ".");
    }

    private List<Map<String, Object>> parseAttributes(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?:^|\\s+y\\s+|\\s*,\\s*)([\\p{L}][\\p{L}\\p{N}_-]*)\\s*(?::|de tipo)?\\s+([\\p{L}][\\p{L}\\p{N}_<>?-]*)(?:\\s+como\\s+(pk|clave primaria|identificador(?: principal)?|primary key))?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text.trim());
        while (matcher.find()) result.add(params("name", matcher.group(1), "type", matcher.group(2), "isPrimaryKey", matcher.group(3) != null));
        return result;
    }

    private String[] multiplicities(String value) {
        String normalized = normalize(value == null ? "" : value).replace(" a ", " ").trim();
        if (normalized.matches(".*(uno|1).*muchos.*")) return new String[]{"1", "0..*"};
        if (normalized.matches(".*muchos.*(uno|1).*")) return new String[]{"0..*", "1"};
        Matcher explicit = Pattern.compile("(\\*|\\d+(?:\\.\\.(?:\\d+|\\*))?)\\s+(?:a|y)?\\s*(\\*|\\d+(?:\\.\\.(?:\\d+|\\*))?)").matcher(normalized);
        return explicit.find() ? new String[]{explicit.group(1), explicit.group(2)} : new String[]{"1", "0..*"};
    }

    private String relationType(String value) {
        return switch (normalize(value)) {
            case "asociacion" -> "association";
            case "agregacion" -> "aggregation";
            case "composicion" -> "composition";
            case "herencia" -> "inheritance";
            case "dependencia" -> "dependency";
            default -> null;
        };
    }

    private UmlAiCommandResponse ready(String operation, String targetId, Map<String, Object> parameters, String message) { return response("READY", operation, targetId, parameters, message); }
    private UmlAiCommandResponse error(String message) { return response("ERROR", null, null, Collections.emptyMap(), message); }
    private UmlAiCommandResponse clarification(String message) { return response("CLARIFICATION", null, null, Collections.emptyMap(), message); }
    private UmlAiCommandResponse response(String status, String operation, String targetId, Map<String, Object> parameters, String message) { return UmlAiCommandResponse.builder().status(status).operation(operation).targetId(targetId).parameters(parameters).message(message).build(); }

    private Map<String, Object> params(Object... values) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
    private String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return null; }
    private static String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT); }

    private record Lookup(Map<String, Object> value, UmlAiCommandResponse response) {
        boolean ok() { return value != null; }
        boolean found() { return value != null; }
        String id() { return String.valueOf(value.get("id")); }
    }

    private class ModelIndex {
        private final List<Map<String, Object>> classes;
        private final List<Map<String, Object>> relations;
        @SuppressWarnings("unchecked") ModelIndex(Map<String, Object> model) {
            this.classes = model.get("classes") instanceof List<?> list ? (List<Map<String, Object>>) list : Collections.emptyList();
            this.relations = model.get("relations") instanceof List<?> list ? (List<Map<String, Object>>) list : Collections.emptyList();
        }
        List<Map<String, Object>> classesNamed(String name) { return classes.stream().filter(item -> normalize(String.valueOf(item.get("name"))).equals(normalize(name))).toList(); }
        Lookup classNamed(String name) { List<Map<String, Object>> found = classesNamed(name); if (found.isEmpty()) return new Lookup(null, error("No encontré la clase " + name + ".")); if (found.size() > 1) return new Lookup(null, clarification("Hay más de una clase llamada " + name + ". Indica cuál deseas modificar.")); return new Lookup(found.get(0), null); }
        Lookup classNamedNatural(String name) { List<Map<String, Object>> found = classesNamed(name); if (found.isEmpty()) { String normalized = normalize(name); List<String> candidates = new ArrayList<>(); if (normalized.endsWith("es") && normalized.length() > 2) candidates.add(normalized.substring(0, normalized.length() - 2)); if (normalized.endsWith("s") && normalized.length() > 1) candidates.add(normalized.substring(0, normalized.length() - 1)); found = classes.stream().filter(item -> candidates.contains(normalize(String.valueOf(item.get("name"))))).toList(); } if (found.isEmpty()) return new Lookup(null, error("No encontré la clase " + name + ".")); if (found.size() > 1) return new Lookup(null, clarification("La referencia " + name + " es ambigua. Indica el nombre exacto de la clase.")); return new Lookup(found.get(0), null); }
        @SuppressWarnings("unchecked") Lookup attributeNamed(Map<String, Object> clazz, String name) { List<Map<String, Object>> attrs = clazz.get("attributes") instanceof List<?> list ? (List<Map<String, Object>>) list : Collections.emptyList(); List<Map<String, Object>> found = attrs.stream().filter(item -> normalize(String.valueOf(item.get("name"))).equals(normalize(name))).toList(); if (found.isEmpty()) return new Lookup(null, error("No encontré el atributo " + name + " en " + clazz.get("name") + ".")); if (found.size() > 1) return new Lookup(null, clarification("Hay más de un atributo llamado " + name + ".")); return new Lookup(found.get(0), null); }
        @SuppressWarnings("unchecked") Lookup methodNamed(Map<String, Object> clazz, String name) { List<Map<String, Object>> methods = clazz.get("methods") instanceof List<?> list ? (List<Map<String, Object>>) list : Collections.emptyList(); List<Map<String, Object>> found = methods.stream().filter(item -> normalize(String.valueOf(item.get("name"))).equals(normalize(name))).toList(); if (found.isEmpty()) return new Lookup(null, error("No encontré el método " + name + " en " + clazz.get("name") + ".")); if (found.size() > 1) return new Lookup(null, clarification("Hay más de un método llamado " + name + ".")); return new Lookup(found.get(0), null); }
        Lookup relationBetween(String sourceId, String targetId) { List<Map<String, Object>> found = relations.stream().filter(item -> (sourceId.equals(String.valueOf(item.get("sourceId"))) && targetId.equals(String.valueOf(item.get("targetId")))) || (targetId.equals(String.valueOf(item.get("sourceId"))) && sourceId.equals(String.valueOf(item.get("targetId"))))).toList(); if (found.isEmpty()) return new Lookup(null, error("No encontré una relación entre las clases indicadas.")); if (found.size() > 1) return new Lookup(null, clarification("Hay varias relaciones entre esas clases. Selecciona la relación en el lienzo o especifica su descripción.")); return new Lookup(found.get(0), null); }
    }
}
