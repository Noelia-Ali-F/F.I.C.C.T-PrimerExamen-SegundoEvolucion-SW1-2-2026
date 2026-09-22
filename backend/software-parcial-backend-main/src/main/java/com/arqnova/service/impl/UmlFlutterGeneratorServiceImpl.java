package com.arqnova.service.impl;

import com.arqnova.dto.request.UmlBackendGenerationRequest.UmlAttribute;
import com.arqnova.dto.request.UmlBackendGenerationRequest.UmlClass;
import com.arqnova.dto.request.UmlFlutterGenerationRequest;
import com.arqnova.service.UmlFlutterGeneratorService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class UmlFlutterGeneratorServiceImpl implements UmlFlutterGeneratorService {
    private static final Set<String> TYPES = Set.of("string", "integer", "int", "long", "boolean", "bool",
            "double", "float", "decimal", "bigdecimal", "date", "datetime", "localdate", "localdatetime", "uuid");

    @Override
    public GeneratedFlutter generate(UmlFlutterGenerationRequest request) {
        validate(request);
        String project = snake(request.projectName());
        String root = project + "/";
        List<UmlClass> classes = request.model().classes();
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, root + "pubspec.yaml", pubspec(project));
            put(zip, root + "analysis_options.yaml", "include: package:flutter_lints/flutter.yaml\n");
            put(zip, root + ".gitignore", ".dart_tool/\nbuild/\n.idea/\n.flutter-plugins*\n");
            put(zip, root + "README.md", readme(project));
            put(zip, root + "lib/main.dart", mainFile());
            put(zip, root + "lib/app.dart", appFile(project));
            put(zip, root + "lib/config/api_config.dart", apiConfig(request.backendBaseUrl()));
            put(zip, root + "lib/screens/home_page.dart", homePage(classes));
            for (UmlClass entity : classes) {
                String file = snake(entity.name());
                put(zip, root + "lib/models/" + file + ".dart", model(entity));
                put(zip, root + "lib/services/" + file + "_api_service.dart", apiService(entity));
                put(zip, root + "lib/screens/" + file + "_list_page.dart", listPage(entity));
                put(zip, root + "lib/screens/" + file + "_form_page.dart", formPage(entity));
            }
            put(zip, root + "test/widget_test.dart", widgetTest(project));
            zip.finish();
            return new GeneratedFlutter(project + "-flutter.zip", bytes.toByteArray(), classes.size(), classes.size() * 2 + 1);
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo empaquetar el proyecto Flutter generado.", error);
        }
    }

    private void validate(UmlFlutterGenerationRequest request) {
        if (request == null || blank(request.projectName())) throw new IllegalArgumentException("El nombre del proyecto Flutter es obligatorio.");
        if (request.model() == null || request.model().classes() == null || request.model().classes().isEmpty())
            throw new IllegalArgumentException("El diagrama debe contener al menos una clase para generar Flutter.");
        if (!blank(request.backendBaseUrl()) && !request.backendBaseUrl().matches("https?://[^\\s]+"))
            throw new IllegalArgumentException("La URL del backend debe comenzar con http:// o https://.");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (UmlClass entity : request.model().classes()) {
            if (entity == null || blank(entity.id()) || blank(entity.name())) throw new IllegalArgumentException("Todas las clases deben tener ID y nombre.");
            if (!ids.add(entity.id()) || !names.add(entity.name().trim().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("El modelo contiene clases duplicadas: " + entity.name() + ".");
            Set<String> attributes = new HashSet<>();
            for (UmlAttribute attribute : Optional.ofNullable(entity.attributes()).orElse(List.of())) {
                if (attribute == null || blank(attribute.name()) || blank(attribute.type()))
                    throw new IllegalArgumentException("La clase " + entity.name() + " contiene un atributo incompleto.");
                if (!attributes.add(attribute.name().trim().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("La clase " + entity.name() + " contiene el atributo duplicado " + attribute.name() + ".");
                if (!TYPES.contains(attribute.type().trim().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("La clase " + entity.name() + " contiene el atributo " + attribute.name() + " con tipo " + attribute.type() + " no soportado.");
            }
        }
    }

    private String pubspec(String project) { return """
            name: %s
            description: Frontend Flutter generado por ArqNova desde un modelo UML.
            publish_to: 'none'
            version: 1.0.0+1
            environment:
              sdk: '>=3.5.0 <4.0.0'
            dependencies:
              flutter:
                sdk: flutter
              http: ^1.2.2
            dev_dependencies:
              flutter_test:
                sdk: flutter
              flutter_lints: ^5.0.0
            flutter:
              uses-material-design: true
            """.formatted(project); }

    private String mainFile() { return "import 'package:flutter/material.dart';\nimport 'app.dart';\n\nvoid main() { runApp(const GeneratedApp()); }\n"; }
    private String appFile(String project) { return """
            import 'package:flutter/material.dart';
            import 'screens/home_page.dart';

            class GeneratedApp extends StatelessWidget {
              const GeneratedApp({super.key});
              @override Widget build(BuildContext context) => MaterialApp(
                title: '%s', debugShowCheckedModeBanner: false,
                theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1E3A8A)), useMaterial3: true),
                home: const HomePage(),
              );
            }
            """.formatted(title(project)); }
    private String apiConfig(String configured) { String url = blank(configured) ? "http://10.0.2.2:8080" : configured.trim(); return """
            class ApiConfig {
              ApiConfig._();
              static const String baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '%s');
            }
            """.formatted(escape(url)); }

    private String model(UmlClass entity) {
        String name = pascal(entity.name());
        List<UmlAttribute> attributes = Optional.ofNullable(entity.attributes()).orElse(List.of());
        if (attributes.isEmpty()) return "class " + name + " {\n  const " + name + "();\n  factory " + name + ".fromJson(Map<String, dynamic> json) => const " + name + "();\n  Map<String, dynamic> toJson() => const <String, dynamic>{};\n}\n";
        StringBuilder fields = new StringBuilder(), params = new StringBuilder(), from = new StringBuilder(), to = new StringBuilder();
        for (UmlAttribute attribute : attributes) {
            String field = camel(attribute.name()), type = dartType(attribute.type());
            fields.append("  final ").append(type).append("? ").append(field).append(";\n");
            params.append("    this.").append(field).append(",\n");
            from.append("      ").append(field).append(": ").append(parseExpression(type, "json['" + escape(attribute.name()) + "']")).append(",\n");
            to.append("      '").append(escape(attribute.name())).append("': ").append(encodeExpression(type, field)).append(",\n");
        }
        return "class " + name + " {\n" + fields + "\n  const " + name + "({\n" + params + "  });\n\n" +
                "  factory " + name + ".fromJson(Map<String, dynamic> json) => " + name + "(\n" + from + "  );\n\n" +
                "  Map<String, dynamic> toJson() => <String, dynamic>{\n" + to + "    }..removeWhere((_, value) => value == null);\n}\n";
    }

    private String apiService(UmlClass entity) {
        String name = pascal(entity.name()), file = snake(entity.name()), route = kebab(entity.name());
        String idType = entity.attributes() == null ? "Object" : entity.attributes().stream().filter(a -> Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().map(a -> dartType(a.type())).orElse("int");
        return """
            import 'dart:convert';
            import 'package:http/http.dart' as http;
            import '../config/api_config.dart';
            import '../models/%s.dart';

            class %sApiService {
              const %sApiService({http.Client? client}) : _client = client;
              final http.Client? _client;
              http.Client get client => _client ?? http.Client();
              Uri _uri([Object? id]) => Uri.parse('${ApiConfig.baseUrl}/api/%s${id == null ? '' : '/$id'}');
              Future<List<%s>> listar() async {
                final response = await client.get(_uri()); _check(response);
                return (jsonDecode(response.body) as List).map((item) => %s.fromJson(item as Map<String, dynamic>)).toList();
              }
              Future<%s> obtener(%s id) async { final response = await client.get(_uri(id)); _check(response); return %s.fromJson(jsonDecode(response.body)); }
              Future<%s> crear(%s value) async { final response = await client.post(_uri(), headers: _headers, body: jsonEncode(value.toJson())); _check(response); return %s.fromJson(jsonDecode(response.body)); }
              Future<%s> actualizar(%s id, %s value) async { final response = await client.put(_uri(id), headers: _headers, body: jsonEncode(value.toJson())); _check(response); return %s.fromJson(jsonDecode(response.body)); }
              Future<void> eliminar(%s id) async { final response = await client.delete(_uri(id)); _check(response); }
              static const _headers = {'Content-Type': 'application/json'};
              void _check(http.Response response) { if (response.statusCode < 200 || response.statusCode >= 300) throw ApiException(response.statusCode, response.body); }
            }
            class ApiException implements Exception { const ApiException(this.statusCode, this.message); final int statusCode; final String message; @override String toString() => 'API $statusCode: $message'; }
            """.formatted(file, name, name, route, name, name, name, idType, name, name, name, name, name, idType, name, name, name, idType); }

    private String homePage(List<UmlClass> classes) {
        StringBuilder imports = new StringBuilder(), tiles = new StringBuilder();
        for (UmlClass entity : classes) {
            String file = snake(entity.name()), name = pascal(entity.name());
            imports.append("import '").append(file).append("_list_page.dart';\n");
            tiles.append("        ListTile(title: const Text('").append(escape(entity.name())).append("'), leading: const Icon(Icons.storage), trailing: const Icon(Icons.chevron_right), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ").append(name).append("ListPage()))),\n");
        }
        return "import 'package:flutter/material.dart';\n" + imports + "\nclass HomePage extends StatelessWidget {\n  const HomePage({super.key});\n  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Recursos')), body: ListView(children: [\n" + tiles + "  ]));\n}\n";
    }

    private String listPage(UmlClass entity) {
        String name = pascal(entity.name()), file = snake(entity.name());
        UmlAttribute display = Optional.ofNullable(entity.attributes()).orElse(List.of()).stream().filter(a -> !Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().orElseGet(() -> Optional.ofNullable(entity.attributes()).orElse(List.of()).stream().findFirst().orElse(null));
        UmlAttribute id = Optional.ofNullable(entity.attributes()).orElse(List.of()).stream().filter(a -> Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().orElse(null);
        String label = display == null ? "item.toString()" : "item." + camel(display.name()) + "?.toString() ?? '" + escape(entity.name()) + "'";
        String idExpr = id == null ? "null" : "item." + camel(id.name());
        String deleteButton = id == null ? "" : "IconButton(icon: const Icon(Icons.delete_outline), onPressed: () async { await service.eliminar(" + idExpr + "!); reload(); })";
        return """
            import 'package:flutter/material.dart';
            import '../models/%s.dart';
            import '../services/%s_api_service.dart';
            import '%s_form_page.dart';

            class %sListPage extends StatefulWidget { const %sListPage({super.key}); @override State<%sListPage> createState() => _%sListPageState(); }
            class _%sListPageState extends State<%sListPage> {
              final service = const %sApiService(); late Future<List<%s>> items;
              @override void initState() { super.initState(); items = service.listar(); }
              void reload() => setState(() => items = service.listar());
              @override Widget build(BuildContext context) => Scaffold(
                appBar: AppBar(title: const Text('%s')),
                body: FutureBuilder<List<%s>>(future: items, builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
                  if (snapshot.hasError) return Center(child: Text('No se pudo cargar: ${snapshot.error}'));
                  final values = snapshot.data ?? const <%s>[];
                  if (values.isEmpty) return const Center(child: Text('No hay registros'));
                  return ListView.builder(itemCount: values.length, itemBuilder: (context, index) { final item = values[index]; return ListTile(
                    title: Text(%s),
                    trailing: Wrap(children: [IconButton(icon: const Icon(Icons.edit), onPressed: () async { await Navigator.push(context, MaterialPageRoute(builder: (_) => %sFormPage(initial: item))); reload(); }), %s]),
                  ); });
                }),
                floatingActionButton: FloatingActionButton(onPressed: () async { await Navigator.push(context, MaterialPageRoute(builder: (_) => const %sFormPage())); reload(); }, child: const Icon(Icons.add)),
              );
            }
            """.formatted(file, file, file, name, name, name, name, name, name, name, name, entity.name(), name, name, label, name, deleteButton, name); }

    private String formPage(UmlClass entity) {
        String name = pascal(entity.name()), file = snake(entity.name());
        List<UmlAttribute> attrs = Optional.ofNullable(entity.attributes()).orElse(List.of());
        StringBuilder declarations = new StringBuilder(), init = new StringBuilder(), dispose = new StringBuilder(), fields = new StringBuilder(), args = new StringBuilder();
        UmlAttribute pk = attrs.stream().filter(a -> Boolean.TRUE.equals(a.isPrimaryKey())).findFirst().orElse(null);
        for (UmlAttribute attr : attrs) {
            String field = camel(attr.name()), type = dartType(attr.type());
            declarations.append("  late final TextEditingController _").append(field).append(";\n");
            init.append("    _").append(field).append(" = TextEditingController(text: widget.initial?.").append(field).append("?.toString() ?? '');\n");
            dispose.append("    _").append(field).append(".dispose();\n");
            fields.append("            TextFormField(controller: _").append(field).append(", decoration: const InputDecoration(labelText: '").append(escape(attr.name())).append("'), keyboardType: ").append(numeric(type) ? "TextInputType.number" : "TextInputType.text").append(",),\n            const SizedBox(height: 12),\n");
            args.append("        ").append(field).append(": ").append(inputExpression(type, "_" + field + ".text")).append(",\n");
        }
        String save = pk == null ? "await service.crear(value);" : "final id = widget.initial?." + camel(pk.name()) + "; if (id == null) { await service.crear(value); } else { await service.actualizar(id, value); }";
        return """
            import 'package:flutter/material.dart';
            import '../models/%s.dart';
            import '../services/%s_api_service.dart';

            class %sFormPage extends StatefulWidget { const %sFormPage({super.key, this.initial}); final %s? initial; @override State<%sFormPage> createState() => _%sFormPageState(); }
            class _%sFormPageState extends State<%sFormPage> {
              final service = const %sApiService(); final formKey = GlobalKey<FormState>(); bool saving = false;
            %s
              @override void initState() { super.initState();
            %s  }
              @override void dispose() {
            %s    super.dispose();
              }
              Future<void> save() async { if (!formKey.currentState!.validate()) return; setState(() => saving = true); try {
                  final value = %s(
            %s      );
                  %s
                  if (mounted) Navigator.pop(context);
                } catch (error) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('No se pudo guardar: $error'))); }
                finally { if (mounted) setState(() => saving = false); }
              }
              @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: Text(widget.initial == null ? 'Crear %s' : 'Editar %s')), body: Form(key: formKey, child: ListView(padding: const EdgeInsets.all(20), children: [
            %s        FilledButton(onPressed: saving ? null : save, child: Text(saving ? 'Guardando…' : 'Guardar')),
              ])));
            }
            """.formatted(file, file, name, name, name, name, name, name, name, name, declarations, init, dispose, name, args, save, entity.name(), entity.name(), fields); }

    private String widgetTest(String project) { return """
            import 'package:flutter_test/flutter_test.dart';
            import 'package:%s/app.dart';
            void main() { testWidgets('muestra recursos generados', (tester) async { await tester.pumpWidget(const GeneratedApp()); expect(find.text('Recursos'), findsOneWidget); }); }
            """.formatted(project); }
    private String readme(String project) { return """
            # %s

            Frontend Flutter generado por ArqNova. Incluye modelos Dart, servicios REST y pantallas CRUD.

            ```bash
            flutter pub get
            flutter create . --platforms=android,ios,web
            flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
            ```

            `API_BASE_URL` permite apuntar al backend Spring Boot sin modificar código. En emulador Android, `10.0.2.2` representa la máquina anfitriona.
            """.formatted(project); }

    private String dartType(String type) { return switch (type.trim().toLowerCase(Locale.ROOT)) { case "integer", "int", "long" -> "int"; case "boolean", "bool" -> "bool"; case "double", "float", "decimal", "bigdecimal" -> "double"; case "date", "datetime", "localdate", "localdatetime" -> "DateTime"; default -> "String"; }; }
    private String parseExpression(String type, String source) { return switch (type) { case "int" -> source + " is num ? (" + source + " as num).toInt() : int.tryParse(" + source + "?.toString() ?? '')"; case "double" -> source + " is num ? (" + source + " as num).toDouble() : double.tryParse(" + source + "?.toString() ?? '')"; case "bool" -> source + " as bool?"; case "DateTime" -> source + " == null ? null : DateTime.tryParse(" + source + ".toString())"; default -> source + "?.toString()"; }; }
    private String encodeExpression(String type, String field) { return "DateTime".equals(type) ? field + "?.toIso8601String()" : field; }
    private String inputExpression(String type, String source) { return switch (type) { case "int" -> "int.tryParse(" + source + ")"; case "double" -> "double.tryParse(" + source + ")"; case "bool" -> source + ".toLowerCase() == 'true'"; case "DateTime" -> "DateTime.tryParse(" + source + ")"; default -> source + ".trim().isEmpty ? null : " + source + ".trim()"; }; }
    private boolean numeric(String type) { return "int".equals(type) || "double".equals(type); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String title(String value) { return Arrays.stream(snake(value).split("_")).filter(s -> !s.isBlank()).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).reduce((a,b) -> a + " " + b).orElse("Flutter App"); }
    private String snake(String value) { String v = ascii(value).replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "").toLowerCase(Locale.ROOT); return v.isBlank() ? "generated_flutter_app" : Character.isDigit(v.charAt(0)) ? "app_" + v : v; }
    private String kebab(String value) { return snake(value).replace('_', '-'); }
    private String pascal(String value) { String out = Arrays.stream(snake(value).split("_")).filter(s -> !s.isBlank()).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).reduce(String::concat).orElse("Generated"); return Character.isDigit(out.charAt(0)) ? "Model" + out : out; }
    private String camel(String value) { String out = pascal(value); out = Character.toLowerCase(out.charAt(0)) + out.substring(1); return Set.of("class", "enum", "extends", "with", "void", "final", "const", "new", "return", "switch", "case", "default").contains(out) ? out + "Value" : out; }
    private String ascii(String value) { return Normalizer.normalize(Optional.ofNullable(value).orElse(""), Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }
    private String escape(String value) { return Optional.ofNullable(value).orElse("").replace("\\", "\\\\").replace("'", "\\'"); }
    private void put(ZipOutputStream zip, String path, String content) throws IOException { zip.putNextEntry(new ZipEntry(path)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
}
