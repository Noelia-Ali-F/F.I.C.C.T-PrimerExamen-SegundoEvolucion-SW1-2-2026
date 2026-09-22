package com.arqnova.service;

import com.arqnova.dto.request.UmlAiCommandRequest;
import com.arqnova.service.impl.UmlAiCommandServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UmlAiCommandServiceTest {
    private final UmlAiCommandServiceImpl service = new UmlAiCommandServiceImpl();

    private Map<String, Object> model() {
        return Map.of("classes", List.of(
                Map.of("id", "cliente", "name", "Cliente", "attributes", List.of(
                        Map.of("id", "correo", "name", "correo", "type", "String"),
                        Map.of("id", "edad", "name", "edad", "type", "String"),
                        Map.of("id", "id-cliente", "name", "id", "type", "Long")), "methods", List.of()),
                Map.of("id", "venta", "name", "Venta", "attributes", List.of(), "methods", List.of()),
                Map.of("id", "usuario", "name", "Usuario", "attributes", List.of(), "methods", List.of(
                        Map.of("id", "autenticar", "name", "autenticar", "parameters", "", "returnType", "void"))),
                Map.of("id", "pedido", "name", "Pedido", "attributes", List.of(), "methods", List.of()),
                Map.of("id", "detalle", "name", "Detalle", "attributes", List.of(), "methods", List.of())
        ), "relations", List.of(Map.of("id", "r1", "sourceId", "cliente", "targetId", "venta", "sourceMultiplicity", "1", "targetMultiplicity", "0..*")));
    }

    private com.arqnova.dto.response.UmlAiCommandResponse interpret(String text) {
        return service.interpret(new UmlAiCommandRequest(text, model()));
    }

    @Test void interpretaCreacionDeClaseConAtributos() {
        var result = interpret("Crea una clase Factura con id Long y fecha Date");
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperation()).isEqualTo("CLASS_CREATE");
        assertThat((List<?>) result.getParameters().get("attributes")).hasSize(2);
    }

    @Test void interpretaOperacionesDeAtributoConIdsInternos() {
        var create = interpret("Agrega a Cliente el atributo teléfono de tipo String");
        var update = interpret("Cambia el tipo del atributo correo de Cliente a Email");
        var delete = interpret("Elimina el atributo correo de Cliente");
        assertThat(create.getOperation()).isEqualTo("ATTRIBUTE_CREATE");
        assertThat(create.getParameters().get("classId")).isEqualTo("cliente");
        assertThat(update.getTargetId()).isEqualTo("correo");
        assertThat(update.getParameters().get("type")).isEqualTo("Email");
        assertThat(delete.getOperation()).isEqualTo("ATTRIBUTE_DELETE");
    }

    @Test void interpretaRelacionesYMultiplicidades() {
        var create = interpret("Relaciona Cliente con Venta de uno a muchos");
        var update = interpret("Cambia la multiplicidad de Cliente con Venta a 1 a 1..*");
        var type = interpret("Cambia el tipo de la relación entre Cliente y Venta a composición");
        var delete = interpret("Elimina la relación entre Cliente y Venta");
        var natural = interpret("Un Cliente puede tener muchos Pedidos");
        var relationType = interpret("Cambia la relación Cliente Venta a agregación");
        assertThat(create.getParameters()).containsEntry("sourceId", "cliente").containsEntry("targetId", "venta").containsEntry("targetMultiplicity", "0..*");
        assertThat(update.getOperation()).isEqualTo("RELATION_UPDATE");
        assertThat(type.getParameters().get("type")).isEqualTo("composition");
        assertThat(delete.getTargetId()).isEqualTo("r1");
        assertThat(natural.getParameters()).containsEntry("sourceMultiplicity", "1").containsEntry("targetMultiplicity", "0..*");
        assertThat(relationType.getParameters()).containsEntry("type", "aggregation");
    }

    @Test void noModificaAnteAmbiguedadOElementoInexistente() {
        assertThat(interpret("Haz el diagrama mejor").getStatus()).isEqualTo("CLARIFICATION");
        var missing = interpret("Elimina la clase Fantasma");
        assertThat(missing.getStatus()).isEqualTo("ERROR");
        assertThat(missing.getOperation()).isNull();
    }

    @Test void interpretaRenombrarYEliminarClase() {
        assertThat(interpret("Cambia nombre de Cliente a Comprador").getOperation()).isEqualTo("CLASS_UPDATE");
        assertThat(interpret("Elimina la clase Cliente").getTargetId()).isEqualTo("cliente");
    }

    @Test void aceptaVariantesSegurasDeCrearClase() {
        List<String> commands = List.of("Crea una clase Mascota", "Crea la clase Mascota2", "Crear clase Mascota3",
                "Añade una clase Mascota4", "Agrega la clase Mascota5", "Nueva clase Mascota6",
                "Quiero crear una clase Mascota7", "  CREA   UNA CLASE   Mascota8.  ");
        for (String command : commands) {
            var result = interpret(command);
            assertThat(result.getOperation()).as(command).isEqualTo("CLASS_CREATE");
            assertThat(result.getInterpreter()).isEqualTo("DETERMINISTIC_FALLBACK");
            assertThat(result.isFallbackUsed()).isTrue();
        }
    }

    @Test void interpretaPkSoloCuandoEsExplicita() {
        var withoutId = Map.<String, Object>of("classes", List.of(
                Map.of("id", "cliente", "name", "Cliente", "attributes", List.of(), "methods", List.of())), "relations", List.of());
        var create = service.interpret(new UmlAiCommandRequest("Agrega id Long como PK a Cliente", withoutId));
        var mark = interpret("Marca id como clave primaria en Cliente");
        var unmark = interpret("Desmarca id como identificador en Cliente");
        assertThat(create.getParameters()).containsEntry("isPrimaryKey", true);
        assertThat(mark.getOperation()).isEqualTo("ATTRIBUTE_UPDATE");
        assertThat(mark.getParameters()).containsEntry("isPrimaryKey", true);
        assertThat(unmark.getParameters()).containsEntry("isPrimaryKey", false);
        assertThat(interpret("Agrega a Cliente un atributo otroId de tipo Long").getParameters()).containsEntry("isPrimaryKey", false);
    }

    @Test void interpretaVariantesDeAtributos() {
        assertThat(interpret("Ponle a Cliente un campo apodo String").getOperation()).isEqualTo("ATTRIBUTE_CREATE");
        assertThat(interpret("Crea nacimiento Date en Cliente").getOperation()).isEqualTo("ATTRIBUTE_CREATE");
        assertThat(interpret("Cambia edad de Cliente a Double").getParameters()).containsEntry("type", "Double");
        assertThat(interpret("Elimina correo de Cliente").getOperation()).isEqualTo("ATTRIBUTE_DELETE");
    }

    @Test void interpretaCrearEditarYEliminarMetodos() {
        var create = interpret("Agrega a Usuario el método validar con parámetros correo String y clave String y retorno Boolean");
        assertThat(create.getOperation()).isEqualTo("METHOD_CREATE");
        assertThat(create.getParameters()).containsEntry("classId", "usuario").containsEntry("parameters", "correo: String, clave: String").containsEntry("returnType", "Boolean");
        var rename = interpret("Renombra autenticar de Usuario a validarCredenciales");
        assertThat(rename.getOperation()).isEqualTo("METHOD_UPDATE");
        assertThat(rename.getTargetId()).isEqualTo("autenticar");
        assertThat(interpret("Cambia el retorno de autenticar en Usuario a Boolean").getParameters()).containsEntry("returnType", "Boolean");
        assertThat(interpret("Elimina el método autenticar de Usuario").getOperation()).isEqualTo("METHOD_DELETE");
    }

    @Test void interpretaTiposDeRelacion() {
        assertThat(interpret("Relaciona Pedido con Detalle mediante composición").getParameters()).containsEntry("type", "composition");
        assertThat(interpret("Haz que Cliente herede de Usuario").getParameters()).containsEntry("type", "inheritance");
        assertThat(interpret("Crea una dependencia de Cliente hacia Pedido").getParameters()).containsEntry("type", "dependency");
    }

    @Test void claseCompuestaConPkExplicita() {
        var result = interpret("Crea una clase Persona con id Long como PK, nombre String y correo String");
        var attributes = (List<Map<String, Object>>) result.getParameters().get("attributes");
        assertThat(attributes).hasSize(3);
        assertThat(attributes.get(0)).containsEntry("name", "id").containsEntry("isPrimaryKey", true);
    }
}
