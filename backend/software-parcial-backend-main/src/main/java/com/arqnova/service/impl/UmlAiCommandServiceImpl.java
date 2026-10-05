package com.arqnova.service.impl;

import com.arqnova.dto.request.UmlAiCommandRequest;
import com.arqnova.dto.response.UmlAiCommandResponse;
import com.arqnova.dto.response.UmlAiOperationResponse;
import com.arqnova.service.OpenAiUmlInterpreter;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UmlAiCommandServiceImpl implements UmlAiCommandService {
    private static final String NAME = "([\\p{L}][\\p{L}\\p{N}_-]*)";
    private static final String ASSOCIATION_CLASS_NAME = "([\\p{L}][\\p{L}\\p{N}_-]*(?:\\s+[\\p{L}][\\p{L}\\p{N}_-]*)*?)";
    private static final String MULTIPLICITY = "(cero o muchos|cero o varios|uno o muchos|uno o más|uno o mas|cero o uno|tantos|muchos|varios|uno|cero|\\d+(?:\\.\\.(?:\\d+|\\*))?|\\*|n)";
    private OpenAiUmlInterpreter openAi;

    @Autowired(required = false)
    void setOpenAi(OpenAiUmlInterpreter openAi) { this.openAi = openAi; }

    @Override
    public UmlAiCommandResponse interpret(UmlAiCommandRequest request) {
        UmlAiCommandResponse singleClass = interpretSingleClassCreation(request);
        if (singleClass != null) return singleClass;
        UmlAiCommandResponse batchCreation = interpretBatchCreation(request);
        if (batchCreation != null) return batchCreation;
        UmlAiCommandResponse compositeCreation = interpretCompositeCreation(request);
        if (compositeCreation != null) return compositeCreation;
        UmlAiCommandResponse explicitAttributeType = interpretExplicitAttributeType(request);
        if (explicitAttributeType != null) return completeFallback(explicitAttributeType);
        UmlAiCommandResponse explicitAttributeCreation = interpretExplicitAttributeCreation(request);
        if (explicitAttributeCreation != null) return completeFallback(explicitAttributeCreation);
        UmlAiCommandResponse explicitMultiplicity = interpretExplicitRelationMultiplicity(request);
        if (explicitMultiplicity != null) return completeFallback(explicitMultiplicity);
        UmlAiCommandResponse spokenMultiplicity = interpretSpokenRelationMultiplicity(request);
        if (spokenMultiplicity != null) return completeFallback(spokenMultiplicity);
        UmlAiCommandResponse explicitMethod = interpretExplicitMethod(request);
        if (explicitMethod != null) return completeFallback(explicitMethod);
        UmlAiCommandResponse explicitAssociation = interpretExplicitAssociation(request);
        if (explicitAssociation != null) return explicitAssociation;
        if (openAi != null && openAi.isConfigured()) {
            try {
                return openAi.interpret(request);
            } catch (Exception exception) {
                log.warn("OpenAi UML no disponible; se usa el parser seguro de respaldo: {}", exception.getMessage());
            }
        }
        return completeFallback(interpretDeterministic(request));
    }

    private UmlAiCommandResponse interpretSingleClassCreation(UmlAiCommandRequest request) {
        String instruction = cleanInstruction(request.getInstruction());
        Matcher classMentions = Pattern.compile("\\b(?:crea|crear|cre|genera|hazme|haceme|agrega|añade|anade)\\s+(?:(?:una|la)\\s+)?clase\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(instruction);
        int classCount = 0;
        while (classMentions.find()) classCount++;
        if (classCount != 1 || Pattern.compile("\\b(?:relaciona|asocia|conecta|une|relaci[oó]n)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(instruction).find()) return null;
        String[] clauses = instruction.split("(?<=[.!?])\\s+");
        if (clauses.length > 2) return null;
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Matcher clazz = Pattern.compile("(?:quiero\\s+|por favor\\s+)?(?:crea|crear|cre|genera|hazme|haceme|agrega|añade|anade)\\s+"
                + "(?:(?:una|la)\\s+)?clase\\s+" + NAME + "\\s+(?:con|que tenga|que tiene)\\s+(?:(?:los|sus)\\s+)?(?:atributos?|campos?)\\s+(.+)", flags)
                .matcher(clauses[0].trim().replaceAll("[.!?]+$", ""));
        if (!clazz.matches()) return null;
        String name = clazz.group(1);
        if (!new ModelIndex(request.getModel()).classesNamed(name).isEmpty())
            return completeFallback(clarification("La clase " + name + " ya existe. Indica otro nombre o solicita modificarla."));
        String remainder = clazz.group(2).trim();
        Matcher methodStart = Pattern.compile("(?:^|\\s*[,;]\\s*|\\s+y\\s+|\\s+con\\s+)(?=(?:(?:con\\s+)?(?:el\\s+|un\\s+|los\\s+)?m[eé]todos?\\b))", flags).matcher(remainder);
        String methodsText = clauses.length == 2 ? clauses[1].trim().replaceAll("[.!?]+$", "") : null;
        if (methodStart.find()) {
            if (methodsText != null) return completeFallback(clarification("Indica los métodos una sola vez para evitar duplicados."));
            methodsText = remainder.substring(methodStart.end()).trim();
            remainder = remainder.substring(0, methodStart.start()).trim();
        }
        List<Map<String, Object>> attributes = parseBatchAttributes(remainder);
        if (attributes.isEmpty()) return completeFallback(clarification("Indica atributos separados por comas; por ejemplo: id cliente, nombre, dirección, celular."));
        String id = "class-" + UUID.randomUUID();
        List<UmlAiOperationResponse> operations = new ArrayList<>();
        operations.add(UmlAiOperationResponse.builder().type("CLASS_CREATE").elementType("class")
                .parameters(params("id", id, "name", name, "attributes", attributes)).build());
        if (methodsText != null) {
            Matcher methods = Pattern.compile("(?:(?:y|agrega|añade|anade|crea|ponle|con)\\s+)*(?:(?:el|un|los)\\s+)?m[eé]todos?\\s+(.+)", flags).matcher(methodsText);
            if (!methods.matches()) return completeFallback(clarification("Indica los métodos por nombre, separados por comas."));
            for (String rawMethod : methods.group(1).split("\\s*,\\s*|\\s+y\\s+")) {
                Matcher method = Pattern.compile("^" + NAME + "(?:\\s+(?:que devuelve|con retorno|retorno|:)\\s+" + NAME + ")?$", flags).matcher(rawMethod.trim());
                if (!method.matches()) return completeFallback(clarification("Indica cada método como nombre o nombre con retorno; por ejemplo: calcularTotal con retorno Double."));
                operations.add(UmlAiOperationResponse.builder().type("METHOD_CREATE").elementType("method")
                        .parameters(params("classId", id, "name", method.group(1), "parameters", "", "returnType", method.group(2) == null ? "void" : method.group(2))).build());
            }
        }
        return UmlAiCommandResponse.builder().status("READY").operation("CLASS_CREATE")
                .parameters(operations.get(0).getParameters()).operations(operations)
                .message("Crearé la clase " + name + " con sus atributos" + (methodsText == null ? "." : " y métodos."))
                .interpreter("DETERMINISTIC_FALLBACK").fallbackUsed(true).build();
    }

    private UmlAiCommandResponse interpretBatchCreation(UmlAiCommandRequest request) {
        String instruction = cleanInstruction(request.getInstruction());
        String[] clauses = instruction.split("(?<=[.!?])\\s+");
        if (clauses.length < 3) return null;
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern classPattern = Pattern.compile("(?:crea|crear|cre|también|tambien)\\s+(?:(?:una|la)\\s+)?clase\\s+" + NAME
                + "\\s+con\\s+(?:(?:los|sus)\\s+)?atributos?\\s+(.+)", flags);
        Pattern relationPattern = Pattern.compile("(?:y\\s+)?(?:(?:pon|pone|agrega|crea)\\s+)?(?:una\\s+)?relaci[oó]n\\s+"
                + "(?:entre\\s+)?(?:la\\s+)?clase\\s+" + NAME + "\\s+(?:con|y)\\s+(?:(?:la)\\s+)?(?:clase\\s+)?" + NAME
                + "\\s+(?:(?:de\\s+)?multiplicidad(?:es)?(?:\\s+de)?|de)\\s+" + MULTIPLICITY + "\\s+a\\s+" + MULTIPLICITY, flags);
        ModelIndex model = new ModelIndex(request.getModel());
        Map<String, String> ids = new LinkedHashMap<>();
        List<UmlAiOperationResponse> operations = new ArrayList<>();
        List<String[]> relations = new ArrayList<>();
        for (String rawClause : clauses) {
            String clause = rawClause.trim().replaceAll("[.!?]+$", "").trim();
            Matcher clazz = classPattern.matcher(clause);
            if (clazz.matches()) {
                String name = clazz.group(1);
                if (ids.containsKey(normalize(name)) || !model.classesNamed(name).isEmpty())
                    return completeFallback(clarification("La clase " + name + " ya existe. Indica nombres únicos."));
                List<Map<String, Object>> attributes = parseBatchAttributes(clazz.group(2));
                if (attributes.isEmpty()) return completeFallback(clarification("Indica los atributos de " + name + " separados por comas."));
                String id = "class-" + UUID.randomUUID();
                ids.put(normalize(name), id);
                operations.add(UmlAiOperationResponse.builder().type("CLASS_CREATE").elementType("class")
                        .parameters(params("id", id, "name", name, "attributes", attributes)).build());
                continue;
            }
            Matcher relation = relationPattern.matcher(clause);
            if (relation.matches()) {
                relations.add(new String[]{relation.group(1), relation.group(2), multiplicityValue(relation.group(3)), multiplicityValue(relation.group(4))});
                continue;
            }
            return null;
        }
        if (ids.size() < 2 || relations.isEmpty()) return null;
        for (String[] relation : relations) {
            String sourceId = ids.get(normalize(relation[0])), targetId = ids.get(normalize(relation[1]));
            if (sourceId == null || targetId == null || sourceId.equals(targetId))
                return completeFallback(clarification("Las relaciones deben referirse a dos clases distintas creadas en la misma instrucción."));
            operations.add(UmlAiOperationResponse.builder().type("RELATION_CREATE").elementType("relation")
                    .parameters(params("sourceId", sourceId, "targetId", targetId, "type", "association", "label", "",
                            "sourceMultiplicity", relation[2], "targetMultiplicity", relation[3])).build());
        }
        return UmlAiCommandResponse.builder().status("READY").operations(operations)
                .message("Crearé las clases, sus atributos y las relaciones indicadas. Los atributos sin tipo explícito usarán String.")
                .interpreter("DETERMINISTIC_FALLBACK").fallbackUsed(true).build();
    }

    private List<Map<String, Object>> parseBatchAttributes(String text) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String part : text.split("\\s*,\\s*|\\s+y\\s+")) {
            String attribute = part.trim();
            Matcher matcher = Pattern.compile("^([\\p{L}][\\p{L}\\p{N}_-]*(?:\\s+[\\p{L}][\\p{L}\\p{N}_-]*)?)(?:\\s*:\\s*|\\s+de tipo\\s+)([\\p{L}][\\p{L}\\p{N}_<>?-]*)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(attribute);
            String name;
            String type;
            if (matcher.matches()) { name = matcher.group(1); type = matcher.group(2); }
            else if (attribute.matches("[\\p{L}][\\p{L}\\p{N}_-]*(?:\\s+[\\p{L}][\\p{L}\\p{N}_-]*)?")) { name = attribute; type = "String"; }
            else return Collections.emptyList();
            String[] words = name.split("\\s+");
            if (words.length == 2) name = words[0] + words[1].substring(0, 1).toUpperCase(Locale.ROOT) + words[1].substring(1);
            String finalName = name;
            if (result.stream().anyMatch(item -> normalize(String.valueOf(item.get("name"))).equals(normalize(finalName)))) return Collections.emptyList();
            result.add(params("name", name, "type", type, "isPrimaryKey", false));
        }
        return result;
    }

    private UmlAiCommandResponse interpretCompositeCreation(UmlAiCommandRequest request) {
        String instruction = cleanInstruction(request.getInstruction());
        int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Matcher classes = Pattern.compile("\\b(?:crea|crear|créame|creame|genera|hazme)\\s+(?:una\\s+|otra\\s+)?clase\\s+" + NAME
                + "(?:\\s+con\\s+atributos?\\s+(.+?))?(?=\\s*,?\\s+(?:crea|crear|agrega|añade|anade|relaciona|asocia)\\b|$)", flags).matcher(instruction);
        List<UmlAiOperationResponse> operations = new ArrayList<>();
        Map<String, String> ids = new LinkedHashMap<>();
        ModelIndex model = new ModelIndex(request.getModel());
        while (classes.find()) {
            String name = classes.group(1), key = normalize(name);
            if (ids.containsKey(key) || !model.classesNamed(name).isEmpty())
                return completeFallback(clarification("La clase " + name + " ya existe. Indica nombres únicos."));
            List<Map<String, Object>> attributes = parseAttributes(classes.group(2));
            if (classes.group(2) != null && attributes.isEmpty())
                return completeFallback(clarification("Indica los atributos como nombre y tipo; por ejemplo: id Integer y fecha Date."));
            String id = "class-" + UUID.randomUUID();
            ids.put(key, id);
            operations.add(UmlAiOperationResponse.builder().type("CLASS_CREATE").elementType("class")
                    .parameters(params("id", id, "name", name, "attributes", attributes)).build());
        }
        if (operations.size() < 2) return null;
        Matcher method = Pattern.compile("\\b(?:agrega|añade|anade|crea|ponle)\\s+(?:el\\s+)?m[eé]todo\\s+" + NAME
                + "(?:\\s+sin\\s+par[aá]metros)?\\s+(?:que\\s+devuelve|con\\s+retorno|retorno)\\s+" + NAME
                + "\\s+(?:a|en)\\s+(?:la\\s+clase\\s+)?" + NAME, flags).matcher(instruction);
        Matcher relation = Pattern.compile("\\b(?:relaciona|asocia|conecta|une)\\s+" + NAME + "\\s+(?:con|y)\\s+" + NAME
                + "\\s+(?:con\\s+(?:las\\s+)?multiplicidades?\\s+|de\\s+)" + MULTIPLICITY + "\\s+a\\s+" + MULTIPLICITY, flags).matcher(instruction);
        if (!method.find() || !relation.find()) return null;
        String methodClassId = ids.get(normalize(method.group(3)));
        String sourceId = ids.get(normalize(relation.group(1))), targetId = ids.get(normalize(relation.group(2)));
        if (methodClassId == null || sourceId == null || targetId == null || sourceId.equals(targetId))
            return completeFallback(clarification("Indica un método para una de las clases nuevas y relaciona dos clases distintas por nombre."));
        operations.add(UmlAiOperationResponse.builder().type("METHOD_CREATE").elementType("method")
                .parameters(params("classId", methodClassId, "name", method.group(1), "parameters", "", "returnType", method.group(2))).build());
        operations.add(UmlAiOperationResponse.builder().type("RELATION_CREATE").elementType("relation")
                .parameters(params("sourceId", sourceId, "targetId", targetId, "type", "association", "label", "",
                        "sourceMultiplicity", multiplicityValue(relation.group(3)), "targetMultiplicity", multiplicityValue(relation.group(4)))).build());
        return UmlAiCommandResponse.builder().status("READY").operations(operations).message("Crearé las clases, sus atributos, el método y la relación indicada.")
                .interpreter("DETERMINISTIC_FALLBACK").fallbackUsed(true).build();
    }

    private UmlAiCommandResponse interpretExplicitAttributeType(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        Matcher matcher = match(text, "(?:en la clase |a la clase )" + NAME
                + " (?:el |al )?(?:atributo|campo) " + NAME
                + " (?:ponle|ponele|cambiale|cámbiale|asigna|cambia|modifica) (?:el )?(?:tipo |de tipo )" + NAME);
        String className;
        String attributeName;
        String requestedType;
        if (matcher.matches()) {
            className = matcher.group(1);
            attributeName = matcher.group(2);
            requestedType = matcher.group(3);
        } else {
            matcher = match(text, "(?:cambia|cambiar|modifica|modificar|actualiza|actualizar) (?:el )?tipo (?:del|de) (?:atributo|campo) "
                    + NAME + " (?:de|en) (?:la )?clase " + NAME + " (?:a|por) (?:(?:el )?tipo )?" + NAME);
            if (matcher.matches()) {
                attributeName = matcher.group(1);
                className = matcher.group(2);
                requestedType = matcher.group(3);
            } else {
                matcher = match(text, "(?:cambia|cambiar|modifica|modificar|actualiza|actualizar) (?:el )?(?:atributo|campo) "
                        + NAME + " (?:a|por) (?:el )?tipo " + NAME + " (?:de|en) (?:la )?clase " + NAME);
                if (matcher.matches()) {
                    attributeName = matcher.group(1);
                    requestedType = matcher.group(2);
                    className = matcher.group(3);
                } else {
                    matcher = match(text, "(?:en|de) (?:(?:la )?clase )?" + NAME
                            + " (?:cambia|modifica|actualiza|ponle|ponele|asigna) (?:el )?tipo (?:del? )?(?:atributo |campo )?"
                            + NAME + " (?:a|por|a tipo) " + NAME);
                    if (matcher.matches()) {
                        className = matcher.group(1);
                        attributeName = matcher.group(2);
                        requestedType = matcher.group(3);
                    } else {
                        matcher = match(text, "(?:ponle|ponele|asigna|establece) (?:el )?tipo " + NAME
                                + " (?:al|para el|a el) (?:atributo|campo) " + NAME + " (?:de|en) (?:(?:la )?clase )?" + NAME);
                        if (!matcher.matches()) return null;
                        requestedType = matcher.group(1);
                        attributeName = matcher.group(2);
                        className = matcher.group(3);
                    }
                }
            }
        }
        String type = switch (normalize(requestedType)) {
            case "entero", "integer", "int" -> "Integer";
            case "cadena", "texto", "string" -> "String";
            case "booleano", "boolean" -> "Boolean";
            case "decimal", "double" -> "Double";
            case "fecha", "date" -> "Date";
            default -> requestedType;
        };
        if (!List.of("String", "Integer", "Long", "Boolean", "Double", "Float", "BigDecimal", "Date", "DateTime", "UUID").contains(type))
            return clarification("Indica un tipo compatible: String, Integer, Long, Boolean, Double, Float, BigDecimal, Date, DateTime o UUID.");
        return updateAttribute(new ModelIndex(request.getModel()), className, attributeName, "type", type);
    }

    private UmlAiCommandResponse interpretExplicitMethod(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        String identifier = "[\\p{L}][\\p{L}\\p{N}_-]*";
        String action = "(?:crea|crear|agrega|agregar|añade|anade|ponle|incorpora)";
        String method = "(?:(?:el|un|nuevo)\\s+)?m[eé]todo\\s+(?<method>" + identifier
                + ")(?:\\((?<signature>[^)]*)\\))?(?:\\s*:\\s*(?<signatureReturn>" + identifier + "))?";
        String clazz = "(?:(?:la\\s+)?clase\\s+)?(?<class>" + identifier + ")";
        Matcher matcher = match(text, action + "\\s+" + method + "\\s+(?:a|en|para)\\s+" + clazz + "(?:\\s+(?<details>.+))?");
        if (!matcher.matches()) matcher = match(text, action + "\\s+(?:a|en)\\s+" + clazz + "\\s+" + method + "(?:\\s+(?<details>.+))?");
        if (matcher.matches()) {
            String parameters = matcher.group("signature") == null ? "" : matcher.group("signature").trim();
            String returnType = matcher.group("signatureReturn");
            String details = matcher.group("details");
            if (details != null) {
                Matcher withParameters = match(details, "(?:con\\s+)?par[aá]metros?\\s+(.+?)(?:\\s+(?:y\\s+)?(?:con retorno|retorno|que devuelve|devuelve|retorna)\\s+" + NAME + ")?");
                Matcher withReturn = match(details, "(?:con retorno|retorno|que devuelve|devuelve|retorna)\\s+" + NAME);
                if (withParameters.matches()) {
                    if (!parameters.isBlank()) return clarification("Indica los parámetros una sola vez para evitar duplicados.");
                    parameters = withParameters.group(1);
                    if (withParameters.group(2) != null) {
                        if (returnType != null) return clarification("Indica el retorno una sola vez para evitar ambigüedades.");
                        returnType = withParameters.group(2);
                    }
                } else if (withReturn.matches()) {
                    if (returnType != null) return clarification("Indica el retorno una sola vez para evitar ambigüedades.");
                    returnType = withReturn.group(1);
                } else return null;
            }
            String parsedParameters = parseParameters(parameters);
            if (!parameters.isBlank() && parsedParameters.isBlank())
                return clarification("Indica cada parámetro como nombre y tipo; por ejemplo: cliente: String.");
            return createMethod(new ModelIndex(request.getModel()), matcher.group("class"), matcher.group("method"),
                    parsedParameters, returnType == null ? "void" : returnType);
        }

        matcher = match(text, "(?:renombra|cambia|modifica|actualiza)\\s+(?:el\\s+)?(?:nombre\\s+(?:del\\s+)?)?m[eé]todo\\s+"
                + NAME + "\\s+(?:de|en)\\s+(?:(?:la\\s+)?clase\\s+)?" + NAME + "\\s+(?:a|por)\\s+" + NAME);
        if (matcher.matches()) return updateMethod(new ModelIndex(request.getModel()), matcher.group(2), matcher.group(1), "name", matcher.group(3));

        matcher = match(text, "(?:cambia|modifica|actualiza)\\s+(?:el\\s+)?(?:tipo de retorno|retorno)\\s+(?:del\\s+)?m[eé]todo\\s+"
                + NAME + "\\s+(?:de|en)\\s+(?:(?:la\\s+)?clase\\s+)?" + NAME + "\\s+(?:a|por)\\s+" + NAME);
        if (matcher.matches()) return updateMethod(new ModelIndex(request.getModel()), matcher.group(2), matcher.group(1), "returnType", matcher.group(3));

        matcher = match(text, "(?:cambia|modifica|actualiza)\\s+(?:el\\s+)?m[eé]todo\\s+" + NAME
                + "\\s+(?:de|en)\\s+(?:(?:la\\s+)?clase\\s+)?" + NAME + "\\s+(?:para que devuelva|para que retorne|que devuelva|que retorne)\\s+" + NAME);
        if (matcher.matches()) return updateMethod(new ModelIndex(request.getModel()), matcher.group(2), matcher.group(1), "returnType", matcher.group(3));

        matcher = match(text, "(?:cambia|modifica|actualiza)\\s+(?:los\\s+)?par[aá]metros\\s+(?:del\\s+)?m[eé]todo\\s+"
                + NAME + "\\s+(?:de|en)\\s+(?:(?:la\\s+)?clase\\s+)?" + NAME + "\\s+(?:a|por)\\s+(.+)");
        if (matcher.matches()) {
            String parameters = parseParameters(matcher.group(3));
            if (parameters.isBlank()) return clarification("Indica cada parámetro como nombre y tipo; por ejemplo: cliente: String.");
            return updateMethod(new ModelIndex(request.getModel()), matcher.group(2), matcher.group(1), "parameters", parameters);
        }

        matcher = match(text, "(?:elimina|eliminar|borra|quita|suprime)\\s+(?:el\\s+)?m[eé]todo\\s+"
                + NAME + "\\s+(?:de|en)\\s+(?:(?:la\\s+)?clase\\s+)?" + NAME);
        if (matcher.matches()) {
            ModelIndex model = new ModelIndex(request.getModel());
            Lookup owner = model.classNamed(matcher.group(2));
            if (!owner.ok()) return owner.response();
            Lookup found = model.methodNamed(owner.value(), matcher.group(1));
            if (!found.ok()) return found.response();
            return ready("METHOD_DELETE", found.id(), params("classId", owner.id()), "Eliminaré el método " + matcher.group(1) + ".");
        }
        return null;
    }

    private UmlAiCommandResponse interpretExplicitRelationMultiplicity(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        Matcher matcher = match(text, "(?:relaciona|relacion|relación|asocia|conecta|une)\\s+" + ASSOCIATION_CLASS_NAME
                + "\\s+(?:con|y)\\s+" + ASSOCIATION_CLASS_NAME
                + "\\s+(?:pon|pone|ponle|establece|fija|cambia|modifica)\\s+(?:la\\s+)?multiplicidad(?:es)?(?:\\s+de)?\\s+"
                + MULTIPLICITY + "\\s+a\\s+" + MULTIPLICITY);
        if (!matcher.matches()) return null;
        ModelIndex model = new ModelIndex(request.getModel());
        Lookup source = model.classNamed(matcher.group(1).trim());
        if (!source.ok()) return source.response();
        Lookup target = model.classNamed(matcher.group(2).trim());
        if (!target.ok()) return target.response();
        Lookup relation = model.relationBetween(source.id(), target.id());
        if (!relation.ok()) return relation.response();
        String first = multiplicityValue(matcher.group(3));
        String second = multiplicityValue(matcher.group(4));
        boolean sameDirection = source.id().equals(String.valueOf(relation.value().get("sourceId")));
        return ready("RELATION_UPDATE", relation.id(), params("sourceMultiplicity", sameDirection ? first : second,
                "targetMultiplicity", sameDirection ? second : first), "Actualizaré las multiplicidades de la relación.");
    }

    private UmlAiCommandResponse interpretSpokenRelationMultiplicity(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        String classReference = "(?:(?:la\\s+)?clase\\s+(?:de\\s+)?)?";
        Matcher matcher = match(text, "(?:pon|pone|ponle|poner|establece|fija|cambia|modifica|actualiza)\\s+"
                + "(?:la\\s+)?multiplicidad(?:es)?\\s+(?:de|entre)\\s+" + classReference + NAME
                + "\\s+(?:a|con|y)\\s+" + classReference + NAME + "\\s+(?:de|a|en)\\s+"
                + MULTIPLICITY + "\\s+a\\s+" + MULTIPLICITY
                + "(?:\\s+(?:con\\s+)?(?:verbo|etiqueta|nombre)\\s+" + NAME + ")?");
        if (!matcher.matches()) return null;
        ModelIndex model = new ModelIndex(request.getModel());
        Lookup firstClass = model.classNamed(matcher.group(1));
        if (!firstClass.ok()) return firstClass.response();
        Lookup secondClass = model.classNamed(matcher.group(2));
        if (!secondClass.ok()) return secondClass.response();
        Lookup relation = model.relationBetween(firstClass.id(), secondClass.id());
        if (!relation.ok()) return relation.response();
        boolean sameDirection = firstClass.id().equals(String.valueOf(relation.value().get("sourceId")));
        String first = multiplicityValue(matcher.group(3));
        String second = multiplicityValue(matcher.group(4));
        Map<String, Object> parameters = params("sourceMultiplicity", sameDirection ? first : second,
                "targetMultiplicity", sameDirection ? second : first);
        if (matcher.group(5) != null) parameters.put("label", matcher.group(5));
        return ready("RELATION_UPDATE", relation.id(), parameters,
                "Actualizaré las multiplicidades de " + matcher.group(1) + " y " + matcher.group(2) + ".");
    }

    private UmlAiCommandResponse interpretExplicitAssociation(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        String action = "(?:(?:crea|crear|créame|creame|haz|hazme|hace|haceme|genera|genérame|generame|agrega|añade|anade)\\s+)?";
        String relation = "(?:(?:una|la)\\s+)?(?:asociación|asociacion|relación de asociación|relacion de asociacion|relación|relacion)\\s+(?:entre|de)\\s+";
        String classes = "(?:(?:las clases|la clase|clases|clase)\\s+)?" + ASSOCIATION_CLASS_NAME
                + "\\s+(?:y|con|relacionada con|relacionado con)\\s+(?:(?:la clase|clase)\\s+)?" + ASSOCIATION_CLASS_NAME;
        String cardinalities = "\\s+(?:(?:con (?:la )?)?multiplicidad(?:es)?(?: de)?|de)\\s+" + MULTIPLICITY
                + "\\s+a\\s+" + MULTIPLICITY + "(?:\\s+(?:llamada|llamado|con nombre|con verbo)\\s+" + NAME + ")?"
                + "(?:\\s+(?:y\\s+)?(?:una\\s+)?clase de asociación(?: llamada| con nombre)\\s+" + NAME + ")?";
        Matcher matcher = match(text, action + relation + classes + cardinalities);
        if (!matcher.matches()) matcher = match(text, action + "(?:las clases|clases|la clase|clase)\\s+"
                + ASSOCIATION_CLASS_NAME + "\\s+(?:y|con|relacionada con|relacionado con)\\s+(?:(?:la clase|clase)\\s+)?"
                + ASSOCIATION_CLASS_NAME + "\\s+con\\s+(?:(?:una|la)\\s+)?(?:relación de asociación|relacion de asociacion|asociación|asociacion|relación|relacion)"
                + cardinalities);
        if (!matcher.matches()) matcher = match(text, "(?:relaciona|asocia|conecta|une)\\s+" + ASSOCIATION_CLASS_NAME
                + "\\s+(?:con|y)\\s+" + ASSOCIATION_CLASS_NAME + cardinalities);
        if (!matcher.matches()) {
            Matcher incomplete = match(text, action + "(?:(?:una|la)\\s+)?(?:clase|asociación|asociacion|relación|relacion)\\s+"
                    + ASSOCIATION_CLASS_NAME + ".*?(?:multiplicidad|de)\\s+" + MULTIPLICITY + "\\s+a\\s+" + MULTIPLICITY);
            return incomplete.matches() ? completeFallback(clarification("Indica las dos clases de la asociación y la multiplicidad de cada extremo; por ejemplo: Crea una asociación entre Recibo y Factura de 1 a 0..1.")) : null;
        }
        String sourceName = matcher.group(1).trim(), targetName = matcher.group(2).trim();
        if (normalize(sourceName).equals(normalize(targetName)))
            return completeFallback(clarification("Indica dos nombres de clase distintos para esta asociación."));
        ModelIndex model = new ModelIndex(request.getModel());
        List<Map<String, Object>> sourceMatches = model.classesNamed(sourceName);
        List<Map<String, Object>> targetMatches = model.classesNamed(targetName);
        if (sourceMatches.size() > 1 || targetMatches.size() > 1)
            return completeFallback(clarification("Hay varias clases con el mismo nombre. Indica clases únicas antes de asociarlas."));
        String sourceId = sourceMatches.isEmpty() ? "class-" + UUID.randomUUID() : String.valueOf(sourceMatches.get(0).get("id"));
        String targetId = targetMatches.isEmpty() ? "class-" + UUID.randomUUID() : String.valueOf(targetMatches.get(0).get("id"));
        if (!normalize(text).startsWith("relaciona ") && !sourceMatches.isEmpty() && !targetMatches.isEmpty()
                && model.relationBetween(sourceId, targetId).found())
            return completeFallback(clarification("Ya existe una relación entre " + sourceName + " y " + targetName + ". Indica si deseas modificarla."));
        List<UmlAiOperationResponse> operations = new ArrayList<>();
        if (sourceMatches.isEmpty()) operations.add(UmlAiOperationResponse.builder().type("CLASS_CREATE")
                .elementType("class").parameters(params("id", sourceId, "name", sourceName, "attributes", List.of())).build());
        if (targetMatches.isEmpty()) operations.add(UmlAiOperationResponse.builder().type("CLASS_CREATE")
                .elementType("class").parameters(params("id", targetId, "name", targetName, "attributes", List.of())).build());
        operations.add(UmlAiOperationResponse.builder().type("RELATION_CREATE").elementType("relation")
                .parameters(params("sourceId", sourceId, "targetId", targetId, "sourceName", sourceName,
                        "targetName", targetName, "sourceMultiplicity", multiplicityValue(matcher.group(3)),
                        "targetMultiplicity", multiplicityValue(matcher.group(4)), "type", "association",
                        "label", matcher.group(5) == null ? "" : matcher.group(5),
                        "associationClassName", matcher.group(6) == null ? "" : matcher.group(6))).build());
        return UmlAiCommandResponse.builder().status("READY").operation("RELATION_CREATE")
                .parameters(operations.get(operations.size() - 1).getParameters()).operations(operations)
                .message("Crearé la asociación entre " + sourceName + " y " + targetName + " con multiplicidades "
                        + multiplicityValue(matcher.group(3)) + " y " + multiplicityValue(matcher.group(4)) + ".")
                .interpreter("DETERMINISTIC_FALLBACK").fallbackUsed(true).build();
    }

    private UmlAiCommandResponse completeFallback(UmlAiCommandResponse fallback) {
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

    private UmlAiCommandResponse interpretExplicitAttributeCreation(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        Matcher matcher = match(text, "(?:agrega|agregar|añade|anade|crea|crear|pon|poner) (?:un |el )?(?:atributo|campo) "
                + "(?:(?:con (?:el )?nombre|llamado) )?" + NAME
                + "(?: (?:de )?tipo " + NAME + ")? (?:en|a|para) (?:la )?clase " + NAME);
        if (!matcher.matches()) return null;
        return createAttribute(new ModelIndex(request.getModel()), matcher.group(3), matcher.group(1),
                matcher.group(2) == null ? "String" : matcher.group(2), false);
    }

    private UmlAiCommandResponse interpretDeterministic(UmlAiCommandRequest request) {
        String text = cleanInstruction(request.getInstruction());
        ModelIndex model = new ModelIndex(request.getModel());

        Matcher matcher;
        matcher = match(text, "(?:agrega|agregar|añade|anade|crea|crear|ponle) (?:un |el |nuevo )?(?:atributo|campo) "
                + NAME + " (?:de tipo |tipo |:\\s*)" + NAME + " (?:a|en|para) (?:la )?clase " + NAME);
        if (matcher.matches()) return createAttribute(model, matcher.group(3), matcher.group(1), matcher.group(2), false);

        matcher = match(text, "(?:en|a) (?:la )?clase " + NAME + " (?:agrega|agregar|añade|anade|crea|crear|ponle) "
                + "(?:un |el |nuevo )?(?:atributo|campo) " + NAME + " (?:de tipo |tipo |:\\s*)" + NAME);
        if (matcher.matches()) return createAttribute(model, matcher.group(1), matcher.group(2), matcher.group(3), false);

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
        matcher = match(text, "(?:cambia|renombra|modifica|actualiza) (?:la )?clase " + NAME + " (?:a|por) " + NAME);
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
            case "cero", "0" -> "0";
            case "cero o uno" -> "0..1";
            case "tantos", "cero o muchos", "cero o varios", "*", "n" -> "0..*";
            case "muchos", "varios", "uno o muchos", "uno o mas" -> "1..*";
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
