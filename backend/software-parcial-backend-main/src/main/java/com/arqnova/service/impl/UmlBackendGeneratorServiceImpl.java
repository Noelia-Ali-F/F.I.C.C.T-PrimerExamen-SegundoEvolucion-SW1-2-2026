package com.arqnova.service.impl;

import com.arqnova.dto.request.UmlBackendGenerationRequest;
import com.arqnova.dto.request.UmlBackendGenerationRequest.UmlAttribute;
import com.arqnova.dto.request.UmlBackendGenerationRequest.UmlClass;
import com.arqnova.dto.request.UmlBackendGenerationRequest.UmlRelation;
import com.arqnova.service.UmlBackendGeneratorService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UmlBackendGeneratorServiceImpl implements UmlBackendGeneratorService {
    private static final Set<String> MANY = Set.of("*", "0..*", "1..*");
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("string", "String"), Map.entry("integer", "Integer"), Map.entry("int", "Integer"),
            Map.entry("long", "Long"), Map.entry("boolean", "Boolean"), Map.entry("bool", "Boolean"),
            Map.entry("double", "Double"), Map.entry("float", "Float"), Map.entry("decimal", "BigDecimal"),
            Map.entry("bigdecimal", "BigDecimal"), Map.entry("date", "LocalDate"),
            Map.entry("datetime", "LocalDateTime"), Map.entry("localdate", "LocalDate"),
            Map.entry("localdatetime", "LocalDateTime"), Map.entry("uuid", "UUID")
    );

    @Override
    public GeneratedBackend generate(UmlBackendGenerationRequest request) {
        validate(request);
        String artifact = kebab(request.projectName());
        String basePackage = validPackage(request.basePackage()) ? request.basePackage().trim() : "com.generated." + javaIdentifier(artifact, false);
        List<UmlClass> classes = request.model().classes();
        List<UmlRelation> relations = Optional.ofNullable(request.model().relations()).orElse(List.of());
        Map<String, UmlClass> byId = classes.stream().collect(Collectors.toMap(UmlClass::id, Function.identity()));
        Map<String, String> classNames = classes.stream().collect(Collectors.toMap(UmlClass::id, c -> javaIdentifier(c.name(), true)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            String root = artifact + "/";
            String javaRoot = root + "src/main/java/" + basePackage.replace('.', '/') + "/";
            put(zip, root + "pom.xml", pom(artifact));
            put(zip, root + "README.md", readme(artifact));
            put(zip, root + ".gitignore", "target/\n.idea/\n.vscode/\n.env\n");
            put(zip, root + "postman/collection.json", generatePostman(request).content());
            put(zip, root + "src/main/resources/application.properties", properties(artifact, !Boolean.FALSE.equals(request.includeBasicConfig())));
            put(zip, javaRoot + className(artifact) + "Application.java", application(basePackage, className(artifact)));
            for (UmlClass umlClass : classes) {
                String name = classNames.get(umlClass.id());
                put(zip, javaRoot + "model/" + name + ".java", entity(basePackage, umlClass, relations, byId, classNames));
                put(zip, javaRoot + "repository/" + name + "Repository.java", repository(basePackage, name, idType(umlClass)));
                put(zip, javaRoot + "service/" + name + "Service.java", service(basePackage, name, idType(umlClass), idField(umlClass)));
                put(zip, javaRoot + "controller/" + name + "Controller.java", controller(basePackage, name, idType(umlClass)));
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("No se pudo empaquetar el proyecto generado.", impossible);
        }
        return new GeneratedBackend(artifact + ".zip", bytes.toByteArray(), classes.size(), relations.size());
    }

    @Override
    public GeneratedPostman generatePostman(UmlBackendGenerationRequest request) {
        validate(request);
        List<Map<String,Object>> folders = new ArrayList<>();
        for (UmlClass entity : request.model().classes()) {
            String javaName = javaIdentifier(entity.name(), true);
            String route = "/api/" + kebab(javaName);
            String idExample = idExample(entity);
            Map<String,Object> body = exampleBody(entity);
            folders.add(new LinkedHashMap<>(Map.of(
                    "name", javaName,
                    "item", List.of(
                            postmanRequest("Listar " + javaName, "GET", route, null),
                            postmanRequest("Obtener " + javaName + " por ID", "GET", route + "/" + idExample, null),
                            postmanRequest("Crear " + javaName, "POST", route, body),
                            postmanRequest("Actualizar " + javaName, "PUT", route + "/" + idExample, body),
                            postmanRequest("Eliminar " + javaName, "DELETE", route + "/" + idExample, null)
                    )
            )));
        }
        if (folders.isEmpty()) throw new IllegalArgumentException("No existen controladores para generar la colección Postman.");
        Map<String,Object> collection = new LinkedHashMap<>();
        collection.put("info", Map.of(
                "_postman_id", stablePostmanId(request),
                "name", request.projectName() + " - API Spring Boot",
                "description", "Colección generada desde el modelo UML por ArqNova.",
                "schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"));
        collection.put("variable", List.of(
                Map.of("key", "baseUrl", "value", "http://localhost:{{port}}", "type", "string"),
                Map.of("key", "port", "value", "8080", "type", "string")));
        collection.put("item", folders);
        try {
            byte[] content = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(collection);
            return new GeneratedPostman("collection.json", content, folders.size(), folders.size() * 5);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("No se pudo serializar la colección Postman.", error);
        }
    }

    private Map<String,Object> postmanRequest(String name, String method, String path, Map<String,Object> body) {
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("method", method);
        request.put("header", body == null ? List.of() : List.of(Map.of("key", "Content-Type", "value", "application/json", "type", "text")));
        request.put("url", Map.of("raw", "{{baseUrl}}" + path, "host", List.of("{{baseUrl}}"), "path", Arrays.stream(path.substring(1).split("/")).toList()));
        if (body != null) {
            try { request.put("body", Map.of("mode", "raw", "raw", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(body), "options", Map.of("raw", Map.of("language", "json")))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
        }
        return new LinkedHashMap<>(Map.of("name", name, "request", request, "response", List.of()));
    }
    private String stablePostmanId(UmlBackendGenerationRequest request) {
        String signature = request.projectName() + "|" + request.model().classes().stream().map(UmlClass::id).sorted().collect(Collectors.joining("|"));
        return UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String,Object> exampleBody(UmlClass entity) {
        Map<String,Object> body = new LinkedHashMap<>();
        for (UmlAttribute attribute : Optional.ofNullable(entity.attributes()).orElse(List.of())) {
            if (Boolean.TRUE.equals(attribute.isPrimaryKey())) continue;
            body.put(javaIdentifier(attribute.name(), false), exampleValue(attribute.type()));
        }
        return body;
    }
    private Object exampleValue(String type) { return switch (javaType(type)) { case "Integer", "Long" -> 1; case "Double", "Float", "BigDecimal" -> 1.0; case "Boolean" -> true; case "LocalDate" -> "2026-01-01"; case "LocalDateTime" -> "2026-01-01T10:00:00"; case "UUID" -> "00000000-0000-0000-0000-000000000001"; default -> "ejemplo"; }; }
    private String idExample(UmlClass entity) { return Optional.ofNullable(entity.attributes()).orElse(List.of()).stream().filter(a -> Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().map(a -> "UUID".equals(javaType(a.type())) ? "00000000-0000-0000-0000-000000000001" : "String".equals(javaType(a.type())) ? "ejemplo-id" : "1").orElse("1"); }

    private void validate(UmlBackendGenerationRequest request) {
        if (request == null || blank(request.projectName())) throw new IllegalArgumentException("El nombre del proyecto es obligatorio.");
        if (request.model() == null || request.model().classes() == null || request.model().classes().isEmpty())
            throw new IllegalArgumentException("El diagrama debe contener al menos una clase.");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (UmlClass c : request.model().classes()) {
            if (c == null) throw new IllegalArgumentException("El modelo contiene una clase nula.");
            if (blank(c.id())) throw new IllegalArgumentException("La clase " + display(c.name(), "sin nombre") + " no tiene un ID válido.");
            if (blank(c.name())) throw new IllegalArgumentException("La clase con ID " + c.id() + " no tiene nombre.");
            if (!ids.add(c.id())) throw new IllegalArgumentException("La clase " + c.name() + " utiliza el ID duplicado " + c.id() + ".");
            if (!names.add(c.name().trim().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Existe más de una clase con el nombre " + c.name() + ".");
            Set<String> attributes = new HashSet<>();
            for (UmlAttribute a : Optional.ofNullable(c.attributes()).orElse(List.of())) {
                if (a == null) throw new IllegalArgumentException("La clase " + c.name() + " contiene un atributo nulo.");
                if (blank(a.name())) throw new IllegalArgumentException("La clase " + c.name() + " contiene un atributo sin nombre (ID " + display(a.id(), "no informado") + ").");
                if (blank(a.type())) throw new IllegalArgumentException("La clase " + c.name() + " contiene el atributo " + a.name() + " sin tipo de dato.");
                if (!attributes.add(a.name().trim().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("La clase " + c.name() + " contiene el atributo duplicado " + a.name() + ".");
                if (!TYPES.containsKey(a.type().trim().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("La clase " + c.name() + " contiene el atributo " + a.name() + " con tipo " + a.type() + " no soportado.");
            }
        }
        for (UmlRelation r : Optional.ofNullable(request.model().relations()).orElse(List.of())) {
            if (r == null) throw new IllegalArgumentException("El modelo contiene una relación nula.");
            String relation = display(r.id(), "sin ID");
            if (blank(r.sourceId())) throw new IllegalArgumentException("La relación " + relation + " no tiene sourceId.");
            if (blank(r.targetId())) throw new IllegalArgumentException("La relación " + relation + " no tiene targetId.");
            if (!ids.contains(r.sourceId())) throw new IllegalArgumentException("La relación " + relation + " tiene sourceId " + r.sourceId() + " que no corresponde a ninguna clase.");
            if (!ids.contains(r.targetId())) throw new IllegalArgumentException("La relación " + relation + " tiene targetId " + r.targetId() + " que no corresponde a ninguna clase.");
            if (blank(r.sourceMultiplicity())) throw new IllegalArgumentException("La relación " + relation + " no tiene multiplicidad de origen.");
            if (blank(r.targetMultiplicity())) throw new IllegalArgumentException("La relación " + relation + " no tiene multiplicidad de destino.");
        }
        if (!blank(request.basePackage()) && !validPackage(request.basePackage())) throw new IllegalArgumentException("El paquete base no es válido.");
    }

    private String entity(String pkg, UmlClass c, List<UmlRelation> relations, Map<String,UmlClass> byId, Map<String,String> names) {
        String name = names.get(c.id());
        StringBuilder fields = new StringBuilder();
        List<UmlAttribute> attrs = Optional.ofNullable(c.attributes()).orElse(List.of());
        List<UmlAttribute> pks = attrs.stream().filter(a -> Boolean.TRUE.equals(a.isPrimaryKey())).toList();
        if (pks.isEmpty()) fields.append("    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id;\n\n");
        for (UmlAttribute a : attrs) {
            if (Boolean.TRUE.equals(a.isPrimaryKey())) fields.append("    @Id\n");
            fields.append("    @Column(nullable = ").append(Boolean.TRUE.equals(a.isPrimaryKey()) ? "false, unique = true" : "true").append(")\n")
                    .append("    private ").append(javaType(a.type())).append(' ').append(javaIdentifier(a.name(), false)).append(";\n\n");
        }
        for (UmlRelation r : relations) {
            boolean source = c.id().equals(r.sourceId());
            if (!source && !c.id().equals(r.targetId())) continue;
            String otherId = source ? r.targetId() : r.sourceId();
            String other = names.get(otherId);
            boolean thisMany = many(source ? r.sourceMultiplicity() : r.targetMultiplicity());
            boolean otherMany = many(source ? r.targetMultiplicity() : r.sourceMultiplicity());
            String singular = javaIdentifier(other, false), plural = singular + "List";
            if (thisMany && otherMany) {
                if (source) fields.append("    @ManyToMany\n    @JoinTable(name = \"").append(kebab(name)).append('_').append(kebab(other)).append("\", joinColumns = @JoinColumn(name = \"").append(kebab(name)).append("_id\"), inverseJoinColumns = @JoinColumn(name = \"").append(kebab(other)).append("_id\"))\n");
                else fields.append("    @ManyToMany(mappedBy = \"").append(javaIdentifier(name, false)).append("List\")\n    @JsonIgnore\n");
                fields.append("    private Set<").append(other).append("> ").append(plural).append(" = new HashSet<>();\n\n");
            } else if (!thisMany && otherMany) {
                fields.append("    @OneToMany(mappedBy = \"").append(javaIdentifier(name, false)).append("\", cascade = CascadeType.ALL)\n")
                        .append("    private List<").append(other).append("> ").append(plural).append(" = new ArrayList<>();\n\n");
            } else if (thisMany) {
                fields.append("    @ManyToOne\n    @JoinColumn(name = \"").append(kebab(other)).append("_id\")\n    @JsonIgnore\n")
                        .append("    private ").append(other).append(' ').append(singular).append(";\n\n");
            } else {
                if (source) fields.append("    @OneToOne\n    @JoinColumn(name = \"").append(kebab(other)).append("_id\")\n");
                else fields.append("    @OneToOne(mappedBy = \"").append(javaIdentifier(name, false)).append("\")\n    @JsonIgnore\n");
                fields.append("    private ").append(other).append(' ').append(singular).append(";\n\n");
            }
        }
        return "package " + pkg + ".model;\n\nimport com.fasterxml.jackson.annotation.JsonIgnore;\nimport jakarta.persistence.*;\nimport lombok.*;\nimport java.math.BigDecimal;\nimport java.time.*;\nimport java.util.*;\n\n@Entity\n@Table(name = \"" + kebab(name).replace('-', '_') + "\")\n@Getter\n@Setter\n@NoArgsConstructor\n@AllArgsConstructor\npublic class " + name + " {\n\n" + fields + "}\n";
    }

    private String repository(String pkg, String name, String id) { return "package "+pkg+".repository;\n\nimport "+pkg+".model."+name+";\nimport org.springframework.data.jpa.repository.JpaRepository;\n\npublic interface "+name+"Repository extends JpaRepository<"+name+", "+id+"> {}\n"; }
    private String service(String pkg, String n, String id, String idField) { return "package "+pkg+".service;\n\nimport "+pkg+".model."+n+";\nimport "+pkg+".repository."+n+"Repository;\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.stereotype.Service;\nimport java.util.List;\n\n@Service\n@RequiredArgsConstructor\npublic class "+n+"Service {\n    private final "+n+"Repository repository;\n    public List<"+n+"> listar() { return repository.findAll(); }\n    public "+n+" obtener("+id+" id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException(\""+n+" no encontrado\")); }\n    public "+n+" crear("+n+" value) { return repository.save(value); }\n    public "+n+" actualizar("+id+" id, "+n+" value) { obtener(id); value.set"+Character.toUpperCase(idField.charAt(0))+idField.substring(1)+"(id); return repository.save(value); }\n    public void eliminar("+id+" id) { repository.delete(obtener(id)); }\n}\n"; }
    private String controller(String pkg, String n, String id) { String path=kebab(n),v=javaIdentifier(n,false); return "package "+pkg+".controller;\n\nimport "+pkg+".model."+n+";\nimport "+pkg+".service."+n+"Service;\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.http.*;\nimport org.springframework.web.bind.annotation.*;\nimport java.util.List;\n\n@RestController\n@RequestMapping(\"/api/"+path+"\")\n@RequiredArgsConstructor\npublic class "+n+"Controller {\n    private final "+n+"Service service;\n    @GetMapping public List<"+n+"> listar() { return service.listar(); }\n    @GetMapping(\"/{id}\") public "+n+" obtener(@PathVariable "+id+" id) { return service.obtener(id); }\n    @PostMapping public ResponseEntity<"+n+"> crear(@RequestBody "+n+" value) { return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(value)); }\n    @PutMapping(\"/{id}\") public "+n+" actualizar(@PathVariable "+id+" id, @RequestBody "+n+" value) { return service.actualizar(id, value); }\n    @DeleteMapping(\"/{id}\") public ResponseEntity<Void> eliminar(@PathVariable "+id+" id) { service.eliminar(id); return ResponseEntity.noContent().build(); }\n}\n"; }

    private String pom(String artifact) { return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.9</version><relativePath/></parent>
              <groupId>com.generated</groupId><artifactId>%s</artifactId><version>0.0.1-SNAPSHOT</version><properties><java.version>17</java.version></properties>
              <dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency><dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency></dependencies>
              <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
            </project>
            """.formatted(artifact); }
    private String properties(String artifact, boolean includeDatabase) { return "spring.application.name="+artifact+"\n"+(includeDatabase?"spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/"+artifact.replace('-','_')+"}\nspring.datasource.username=${DB_USER:postgres}\nspring.datasource.password=${DB_PASSWORD:postgres}\nspring.jpa.hibernate.ddl-auto=update\nspring.jpa.open-in-view=false\n":""); }
    private String readme(String artifact) { return "# "+artifact+"\n\nBackend Spring Boot generado desde el modelo UML.\n\n1. Crea la base de datos PostgreSQL.\n2. Configura `DB_URL`, `DB_USER` y `DB_PASSWORD`.\n3. Ejecuta `mvn spring-boot:run`.\n"; }
    private String application(String pkg,String n) { return "package "+pkg+";\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class "+n+"Application { public static void main(String[] args) { SpringApplication.run("+n+"Application.class, args); } }\n"; }
    private String idType(UmlClass c) { return Optional.ofNullable(c.attributes()).orElse(List.of()).stream().filter(a->Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().map(a->javaType(a.type())).orElse("Long"); }
    private String idField(UmlClass c) { return Optional.ofNullable(c.attributes()).orElse(List.of()).stream().filter(a->Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().map(a->javaIdentifier(a.name(),false)).orElse("id"); }
    private String javaType(String type) { return TYPES.get(type.trim().toLowerCase(Locale.ROOT)); }
    private boolean many(String multiplicity) { return MANY.contains(multiplicity == null ? "" : multiplicity.trim()); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String display(String value, String fallback) { return blank(value) ? fallback : value; }
    private boolean validPackage(String value) { return !blank(value) && value.matches("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)*"); }
    private String className(String value) { return javaIdentifier(value, true); }
    private String kebab(String value) { String v=ascii(value).replaceAll("([a-z0-9])([A-Z])", "$1-$2").replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase(Locale.ROOT); return v.isBlank()?"uml-backend":v; }
    private String javaIdentifier(String value, boolean upper) { String[] parts=ascii(value).replaceAll("[^A-Za-z0-9]+"," ").trim().split("\\s+"); StringBuilder out=new StringBuilder(); for(String p:parts)if(!p.isBlank())out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); if(out.isEmpty())out.append("Generated"); if(!upper)out.setCharAt(0,Character.toLowerCase(out.charAt(0))); if(Character.isDigit(out.charAt(0)))out.insert(0,upper?'C':'v'); return out.toString(); }
    private String ascii(String value) { return Normalizer.normalize(Optional.ofNullable(value).orElse(""),Normalizer.Form.NFD).replaceAll("\\p{M}",""); }
    private void put(ZipOutputStream zip,String path,String content)throws IOException { zip.putNextEntry(new ZipEntry(path));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry(); }
    private void put(ZipOutputStream zip,String path,byte[] content)throws IOException { zip.putNextEntry(new ZipEntry(path));zip.write(content);zip.closeEntry(); }
}
