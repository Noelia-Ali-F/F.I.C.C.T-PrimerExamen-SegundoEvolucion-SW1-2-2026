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

    @Test void creaUnaClaseConAtributosSinTiposYMetodosOpcionales() {
        var emptyModel = Map.<String, Object>of("classes", List.of(), "relations", List.of());
        for (String instruction : List.of(
                "crea la clase cliente con los atributos id cliente, nombre, direccion,celular.",
                "cre la clase Cliente con atributos id cliente, nombre, dirección y celular",
                "Crear una clase Cliente con sus atributos id cliente, nombre, direccion, celular")) {
            var result = service.interpret(new UmlAiCommandRequest(instruction, emptyModel));
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperations()).extracting("type").containsExactly("CLASS_CREATE");
            assertThat((List<Map<String, Object>>) result.getOperations().get(0).getParameters().get("attributes"))
                    .extracting(attribute -> attribute.get("name"))
                    .containsExactly("idCliente", "nombre", instruction.contains("dirección") ? "dirección" : "direccion", "celular");
        }
        var withMethod = service.interpret(new UmlAiCommandRequest(
                "Crea la clase Cliente con atributos id cliente, nombre, y método registrar con retorno Boolean", emptyModel));
        assertThat(withMethod.getStatus()).isEqualTo("READY");
        assertThat(withMethod.getOperations()).extracting("type").containsExactly("CLASS_CREATE", "METHOD_CREATE");
        assertThat(withMethod.getOperations().get(1).getParameters())
                .containsEntry("classId", withMethod.getOperations().get(0).getParameters().get("id"))
                .containsEntry("name", "registrar").containsEntry("returnType", "Boolean");
    }

    @Test void interpretaEnUnaSolaFraseClasesAtributosMetodoYMultiplicidadesComoTranscripcionDeVoz() {
        var request = new UmlAiCommandRequest(
                "Crea una clase Venta con atributos id Integer y fecha Date, crea una clase Producto con atributo nombre String, agrega el método calcularTotal que devuelve Double a Venta y relaciona Venta con Producto de 1..* a 1..*",
                Map.of("classes", List.of(), "relations", List.of()));
        var result = service.interpret(request);
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).extracting("type")
                .containsExactly("CLASS_CREATE", "CLASS_CREATE", "METHOD_CREATE", "RELATION_CREATE");
        assertThat((List<?>) result.getOperations().get(0).getParameters().get("attributes")).hasSize(2);
        assertThat((List<?>) result.getOperations().get(1).getParameters().get("attributes")).hasSize(1);
        assertThat(result.getOperations().get(2).getParameters()).containsEntry("name", "calcularTotal").containsEntry("returnType", "Double");
        assertThat(result.getOperations().get(3).getParameters()).containsEntry("sourceMultiplicity", "1..*").containsEntry("targetMultiplicity", "1..*");
    }

    @Test void creaTresClasesYDosRelacionesDesdeLaInstruccionRealDelUsuario() {
        var request = new UmlAiCommandRequest(
                "cre la clase cliente con los atributos id cliente, nombre, direccion,celular. tambien la clase venta con los atributos id venta, fecha, total. tambien la clase producto con los atributos id producto,nombre, descripcion. Y pone relacion clase Cliente con Venta de multiplicidad 1 a 1. Y Relacion clase Venta con Producto multiplicidad de 1 a *.",
                Map.of("classes", List.of(), "relations", List.of()));
        var result = service.interpret(request);
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).extracting("type").containsExactly(
                "CLASS_CREATE", "CLASS_CREATE", "CLASS_CREATE", "RELATION_CREATE", "RELATION_CREATE");
        assertThat(result.getOperations().get(0).getParameters()).containsEntry("name", "cliente");
        assertThat((List<?>) result.getOperations().get(0).getParameters().get("attributes")).hasSize(4);
        assertThat(result.getOperations().get(3).getParameters())
                .containsEntry("sourceId", result.getOperations().get(0).getParameters().get("id"))
                .containsEntry("targetId", result.getOperations().get(1).getParameters().get("id"))
                .containsEntry("sourceMultiplicity", "1").containsEntry("targetMultiplicity", "1");
        assertThat(result.getOperations().get(4).getParameters())
                .containsEntry("sourceId", result.getOperations().get(1).getParameters().get("id"))
                .containsEntry("targetId", result.getOperations().get(2).getParameters().get("id"))
                .containsEntry("sourceMultiplicity", "1").containsEntry("targetMultiplicity", "0..*");
    }

    @Test void agregaAtributoNombradoSinTipoExplicitoEnClaseExistente() {
        var result = interpret("agregar un atributo con el nombre descripcion en la clase venta");
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperation()).isEqualTo("ATTRIBUTE_CREATE");
        assertThat(result.getParameters()).containsEntry("classId", "venta")
                .containsEntry("name", "descripcion").containsEntry("type", "String");
        assertThat(result.getOperations()).hasSize(1);
        var typed = interpret("añade un atributo llamado descripcion de tipo Texto a la clase Venta");
        assertThat(typed.getParameters()).containsEntry("classId", "venta")
                .containsEntry("name", "descripcion").containsEntry("type", "Texto");
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
        assertThat(create.getParameters()).containsEntry("sourceId", "cliente").containsEntry("targetId", "venta").containsEntry("targetMultiplicity", "1..*");
        assertThat(update.getOperation()).isEqualTo("RELATION_UPDATE");
        assertThat(type.getParameters().get("type")).isEqualTo("composition");
        assertThat(delete.getTargetId()).isEqualTo("r1");
        assertThat(natural.getParameters()).containsEntry("sourceMultiplicity", "1").containsEntry("targetMultiplicity", "0..*");
        assertThat(relationType.getParameters()).containsEntry("type", "aggregation");
    }

    @Test void actualizaMultiplicidadesExplicitasDeUnaRelacionExistenteSinDuplicarla() {
        var current = Map.<String, Object>of("classes", List.of(
                Map.of("id", "venta", "name", "Venta", "attributes", List.of(), "methods", List.of()),
                Map.of("id", "producto", "name", "Producto", "attributes", List.of(), "methods", List.of())),
                "relations", List.of(Map.of("id", "venta-producto", "sourceId", "venta", "targetId", "producto",
                        "type", "association", "sourceMultiplicity", "1", "targetMultiplicity", "0..*")));
        for (var example : List.of(
                List.of("Relacion Venta con Producto pone multiplicidad de 1..* a 0..*", "1..*", "0..*"),
                List.of("Relacion Venta con Producto pone multiplicidad de 1 a 1", "1", "1"),
                List.of("Relacion Venta con Producto pone multiplicidad de 1 a 1..*", "1", "1..*"),
                List.of("Relacion Venta con Producto pone multiplicidad de 0..* a 1..*", "0..*", "1..*"))) {
            var result = service.interpret(new UmlAiCommandRequest(example.get(0), current));
            assertThat(result.getStatus()).as(example.get(0)).isEqualTo("READY");
            assertThat(result.getOperations()).extracting("type").containsExactly("RELATION_UPDATE");
            assertThat(result.getTargetId()).isEqualTo("venta-producto");
            assertThat(result.getParameters()).containsEntry("sourceMultiplicity", example.get(1))
                    .containsEntry("targetMultiplicity", example.get(2));
        }
        var reverse = service.interpret(new UmlAiCommandRequest(
                "Relacion Producto con Venta pone multiplicidad de 0..* a 1..*", current));
        assertThat(reverse.getParameters()).containsEntry("sourceMultiplicity", "1..*")
                .containsEntry("targetMultiplicity", "0..*");
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

    @Test void aceptaVariantesDeCambioDeTipoSinAdivinarLaClase() {
        for (String instruction : List.of(
                "Cambia el tipo del atributo edad de la clase Cliente a Entero",
                "Modificar tipo de atributo edad en la clase Cliente a Integer",
                "Actualiza el atributo edad a tipo Integer en la clase Cliente")) {
            var result = interpret(instruction);
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("ATTRIBUTE_UPDATE");
            assertThat(result.getTargetId()).isEqualTo("edad");
            assertThat(result.getParameters()).containsEntry("classId", "cliente").containsEntry("type", "Integer");
        }
        assertThat(interpret("Actualiza el atributo edad a tipo Integer").getStatus()).isEqualTo("CLARIFICATION");
    }

    @Test void interpretaTipoDeAtributoDictadoSinAmbiguedadAunqueClaseYAtributoSeLlamenIgual() {
        var notesModel = Map.<String, Object>of("classes", List.of(Map.of(
                "id", "class-notas", "name", "notas", "attributes", List.of(Map.of(
                        "id", "attr-notas", "name", "notas", "type", "String")), "methods", List.of())), "relations", List.of());
        for (String instruction : List.of(
                "en la clase notas el atributo notas ponele tipo entero",
                "En la clase notas al atributo notas ponle el tipo entero.")) {
            var result = service.interpret(new UmlAiCommandRequest(instruction, notesModel));
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("ATTRIBUTE_UPDATE");
            assertThat(result.getTargetId()).isEqualTo("attr-notas");
            assertThat(result.getParameters()).containsEntry("classId", "class-notas").containsEntry("type", "Integer");
            assertThat(result.getOperations()).hasSize(1);
        }
        var missing = service.interpret(new UmlAiCommandRequest(
                "en la clase notas el atributo inexistente ponele tipo entero", notesModel));
        assertThat(missing.getStatus()).isEqualTo("ERROR");
        assertThat(missing.getOperations()).isEmpty();
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

    @Test void aceptaVariantesDeCreacionDeClasesYAtributosPorTexto() {
        var emptyModel = Map.<String, Object>of("classes", List.of(), "relations", List.of());
        for (String instruction : List.of(
                "Crea la clase Factura que tenga atributos codigo, fecha y total",
                "Crea la clase Factura con campos codigo, fecha y total")) {
            var result = service.interpret(new UmlAiCommandRequest(instruction, emptyModel));
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("CLASS_CREATE");
            assertThat((List<?>) result.getParameters().get("attributes")).hasSize(3);
        }
        for (String instruction : List.of(
                "Agrega un atributo telefono tipo String a la clase Cliente",
                "En la clase Cliente crea el campo telefono de tipo String")) {
            var result = interpret(instruction);
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("ATTRIBUTE_CREATE");
            assertThat(result.getParameters()).containsEntry("classId", "cliente").containsEntry("name", "telefono");
        }
        assertThat(interpret("Renombra la clase Cliente a Comprador").getOperation()).isEqualTo("CLASS_UPDATE");
    }

    @Test void aceptaVariantesSegurasDeCambioDeTipo() {
        for (String instruction : List.of(
                "En la clase Cliente cambia el tipo del atributo edad a entero",
                "En Cliente modifica tipo de edad a Integer",
                "Ponle tipo Integer al atributo edad de la clase Cliente")) {
            var result = interpret(instruction);
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("ATTRIBUTE_UPDATE");
            assertThat(result.getTargetId()).isEqualTo("edad");
            assertThat(result.getParameters()).containsEntry("classId", "cliente").containsEntry("type", "Integer");
        }
    }

    @Test void aceptaVariantesDeCreacionEdicionYEliminacionDeMetodos() {
        for (String instruction : List.of(
                "Crea el método calcularTotal en la clase Venta",
                "Agrega a la clase Venta el método calcularTotal",
                "Crea el método calcularTotal(precio: Double): Double en la clase Venta")) {
            var result = interpret(instruction);
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("METHOD_CREATE");
            assertThat(result.getParameters()).containsEntry("classId", "venta").containsEntry("name", "calcularTotal");
        }
        assertThat(interpret("Renombra el método autenticar de la clase Usuario a validarCredenciales").getOperation()).isEqualTo("METHOD_UPDATE");
        assertThat(interpret("Actualiza el retorno del método autenticar de la clase Usuario a Boolean").getParameters()).containsEntry("returnType", "Boolean");
        assertThat(interpret("Elimina el método autenticar de la clase Usuario").getOperation()).isEqualTo("METHOD_DELETE");
    }

    @Test void interpretaMultiplicidadesDictadasSobreRelacionExistenteSinCambiarSuDireccion() {
        for (String instruction : List.of(
                "pone multiplicidad de la clase de venta a cliente de muchos a muchos",
                "pon la multiplicidad entre Venta y Cliente de 1..* a 1..*",
                "actualiza multiplicidades de Venta con Cliente de uno o muchos a uno o muchos")) {
            var result = interpret(instruction);
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperation()).isEqualTo("RELATION_UPDATE");
            assertThat(result.getTargetId()).isEqualTo("r1");
            assertThat(result.getParameters()).containsEntry("sourceMultiplicity", "1..*")
                    .containsEntry("targetMultiplicity", "1..*");
        }
        var reversed = interpret("pon la multiplicidad entre Venta y Cliente de 1 a muchos con verbo vende");
        assertThat(reversed.getParameters()).containsEntry("sourceMultiplicity", "1..*")
                .containsEntry("targetMultiplicity", "1").containsEntry("label", "vende");
        assertThat(interpret("pon multiplicidad de Venta a Pedido de muchos a muchos").getStatus()).isEqualTo("ERROR");
    }

    @Test void interpretaTiposDeRelacion() {
        assertThat(interpret("Relaciona Pedido con Detalle mediante composición").getParameters()).containsEntry("type", "composition");
        assertThat(interpret("Haz que Cliente herede de Usuario").getParameters()).containsEntry("type", "inheritance");
        assertThat(interpret("Crea una dependencia de Cliente hacia Pedido").getParameters()).containsEntry("type", "dependency");
    }

    @Test void creaAsociacionDeEjemploConClasesNuevasYMultiplicidadesExactas() {
        var emptyModel = Map.<String, Object>of("classes", List.of(), "relations", List.of());
        var result = service.interpret(new UmlAiCommandRequest(
                "Crea una asociación entre Recibo y Factura de 1 a 0..1 llamada Tiene", emptyModel));
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).hasSize(3);
        var source = result.getOperations().get(0);
        var target = result.getOperations().get(1);
        var relation = result.getOperations().get(2);
        assertThat(source.getType()).isEqualTo("CLASS_CREATE");
        assertThat(source.getParameters()).containsEntry("name", "Recibo");
        assertThat(target.getType()).isEqualTo("CLASS_CREATE");
        assertThat(target.getParameters()).containsEntry("name", "Factura");
        assertThat(relation.getType()).isEqualTo("RELATION_CREATE");
        assertThat(relation.getParameters()).containsEntry("sourceId", source.getParameters().get("id"))
                .containsEntry("targetId", target.getParameters().get("id"))
                .containsEntry("sourceMultiplicity", "1").containsEntry("targetMultiplicity", "0..1")
                .containsEntry("type", "association").containsEntry("label", "Tiene");
    }

    @Test void asociaClasesExistentesSinDuplicarlasYReconoceVozNatural() {
        var result = interpret("Crea una asociación entre Cliente y Venta de tantos a tantos");
        assertThat(result.getStatus()).isEqualTo("CLARIFICATION"); // Ya existe la relación: no crear duplicados.
        var other = interpret("Relaciona Pedido con Detalle de tantos a tantos");
        assertThat(other.getStatus()).isEqualTo("READY");
        assertThat(other.getOperations()).hasSize(1);
        assertThat(other.getOperations().get(0).getParameters())
                .containsEntry("sourceId", "pedido").containsEntry("targetId", "detalle")
                .containsEntry("sourceMultiplicity", "0..*").containsEntry("targetMultiplicity", "0..*")
                .containsEntry("type", "association");
    }

    @Test void interpretaTextoConVerboYClaseDeAsociacionParaMuchosAMuchos() {
        var result = service.interpret(new UmlAiCommandRequest(
                "Relaciona Venta con Producto de 0..* a 1..* con verbo incluye y clase de asociación llamada DetalleVenta",
                Map.of("classes", List.of(
                        Map.of("id", "venta", "name", "Venta", "attributes", List.of(), "methods", List.of()),
                        Map.of("id", "producto", "name", "Producto", "attributes", List.of(), "methods", List.of())),
                        "relations", List.of())));
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).hasSize(1);
        assertThat(result.getOperations().get(0).getParameters())
                .containsEntry("sourceId", "venta").containsEntry("targetId", "producto")
                .containsEntry("sourceMultiplicity", "0..*").containsEntry("targetMultiplicity", "1..*")
                .containsEntry("label", "incluye").containsEntry("associationClassName", "DetalleVenta");
    }

    @Test void creaSoloClaseFaltanteYNoInventaSegundoExtremo() {
        var result = interpret("Crea una asociación entre Cliente y Factura con multiplicidad de uno a cero o uno");
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).hasSize(2);
        assertThat(result.getOperations().get(0).getParameters()).containsEntry("name", "Factura");
        assertThat(result.getOperations().get(1).getParameters())
                .containsEntry("sourceId", "cliente").containsEntry("sourceMultiplicity", "1")
                .containsEntry("targetMultiplicity", "0..1");
        var incomplete = interpret("Crea una clase Recibo con multiplicidad de tantos a tantos");
        assertThat(incomplete.getStatus()).isEqualTo("CLARIFICATION");
        assertThat(incomplete.getOperations()).isEmpty();
    }

    @Test void creaAsociacionesGenericasSinDependerDelEjemploReciboFactura() {
        var emptyModel = Map.<String, Object>of("classes", List.of(), "relations", List.of());
        for (var caseData : List.of(
                List.of("Hazme una asociación entre Profesor y Curso de muchos a muchos", "Profesor", "Curso", "1..*", "1..*"),
                List.of("Crea las clases Orden de compra y Producto con una asociación de uno a muchos", "Orden de compra", "Producto", "1", "1..*"),
                List.of("Asocia Médico con Paciente con la multiplicidad de cero o uno a uno o más", "Médico", "Paciente", "0..1", "1..*"),
                List.of("Genera una relación entre Autor y Libro de 2..4 a 1", "Autor", "Libro", "2..4", "1"))) {
            var result = service.interpret(new UmlAiCommandRequest(caseData.get(0), emptyModel));
            assertThat(result.getStatus()).as(caseData.get(0)).isEqualTo("READY");
            assertThat(result.getOperations()).as(caseData.get(0)).hasSize(3);
            assertThat(result.getOperations().get(0).getParameters()).containsEntry("name", caseData.get(1));
            assertThat(result.getOperations().get(1).getParameters()).containsEntry("name", caseData.get(2));
            assertThat(result.getOperations().get(2).getParameters())
                    .containsEntry("type", "association")
                    .containsEntry("sourceMultiplicity", caseData.get(3))
                    .containsEntry("targetMultiplicity", caseData.get(4));
        }
    }

    @Test void creaUnoAMuchosComoEnLaImagenSinFijarLosNombresDelEjemplo() {
        var emptyModel = Map.<String, Object>of("classes", List.of(), "relations", List.of());
        for (String instruction : List.of(
                "Crea una asociación entre UnidadMedida y Producto de uno a muchos llamada Pertenece",
                "Crea una asociación entre UnidadMedida y Producto de 1 a 1..* llamada Pertenece")) {
            var result = service.interpret(new UmlAiCommandRequest(instruction, emptyModel));
            assertThat(result.getStatus()).as(instruction).isEqualTo("READY");
            assertThat(result.getOperations()).hasSize(3);
            assertThat(result.getOperations().get(2).getParameters())
                    .containsEntry("type", "association").containsEntry("sourceMultiplicity", "1")
                    .containsEntry("targetMultiplicity", "1..*").containsEntry("label", "Pertenece");
        }
        var optional = service.interpret(new UmlAiCommandRequest(
                "Crea una asociación entre UnidadMedida y Producto de 1 a 0..*", emptyModel));
        assertThat(optional.getOperations().get(2).getParameters()).containsEntry("targetMultiplicity", "0..*");
    }

    @Test void permiteNombrarLaClaseDeAsociacionEnUnaOrdenMuchosAMuchos() {
        var emptyModel = Map.<String, Object>of("classes", List.of(), "relations", List.of());
        var result = service.interpret(new UmlAiCommandRequest(
                "Crea una asociación entre Reserva y Servicio de 0..* a 1..* llamada incluye y una clase de asociación llamada DetalleReserva",
                emptyModel));
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOperations()).hasSize(3);
        assertThat(result.getOperations().get(2).getParameters())
                .containsEntry("sourceMultiplicity", "0..*").containsEntry("targetMultiplicity", "1..*")
                .containsEntry("label", "incluye").containsEntry("associationClassName", "DetalleReserva");
    }

    @Test void claseCompuestaConPkExplicita() {
        var result = interpret("Crea una clase Persona con id Long como PK, nombre String y correo String");
        var attributes = (List<Map<String, Object>>) result.getParameters().get("attributes");
        assertThat(attributes).hasSize(3);
        assertThat(attributes.get(0)).containsEntry("name", "id").containsEntry("isPrimaryKey", true);
    }
}
