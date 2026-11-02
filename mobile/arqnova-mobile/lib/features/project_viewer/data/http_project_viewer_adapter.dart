import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../domain/project_models.dart';
import '../domain/project_viewer_port.dart';

class HttpProjectViewerAdapter implements ProjectViewerPort {
  HttpProjectViewerAdapter({
    http.Client? client,
    this.baseUrl = const String.fromEnvironment(
      'UML_API_BASE_URL',
      defaultValue: 'https://18-116-164-204.sslip.io',
    ),
  }) : _client = client ?? http.Client();
  final http.Client _client;
  final String baseUrl;
  Uri _uri(String path, [Map<String, String>? query]) => Uri.parse(
    '${baseUrl.replaceAll(RegExp(r'/+$'), '')}/api/v1$path',
  ).replace(queryParameters: query);
  Future<dynamic> _read(
    String path,
    ViewerSession session, [
    Map<String, String>? query,
  ]) async {
    try {
      return _decode(
        await _client
            .get(
              _uri(path, query),
              headers: {'Authorization': 'Bearer ${session.token}'},
            )
            .timeout(const Duration(seconds: 25)),
      );
    } on ViewerException {
      rethrow;
    } catch (_) {
      throw const ViewerException(
        'No se pudo conectar. Revisa tu conexión e intenta nuevamente.',
      );
    }
  }

  dynamic _decode(http.Response response) {
    if (response.statusCode == 401) {
      throw const ViewerException(
        'La sesión venció. Vuelve a iniciar sesión.',
        401,
      );
    }
    if (response.statusCode == 403) {
      throw const ViewerException('No tienes acceso a este proyecto.', 403);
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ViewerException(
        'No se pudo completar la consulta (${response.statusCode}).',
        response.statusCode,
      );
    }
    final value = jsonDecode(utf8.decode(response.bodyBytes));
    if (value is! Map || value['exito'] != true) {
      throw const ViewerException(
        'El servidor devolvió una respuesta inválida.',
      );
    }
    return value['datos'];
  }

  @override
  Future<ViewerSession> login(String username, String password) async {
    try {
      final response = await _client
          .post(
            _uri('/auth/login'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'username': username, 'password': password}),
          )
          .timeout(const Duration(seconds: 25));
      if (response.statusCode == 401 || response.statusCode == 400) {
        throw const ViewerException('Correo o contraseña incorrectos.');
      }
      final data = _decode(response) as Map;
      return ViewerSession(
        data['token'] as String,
        data['username'] as String,
        (data['nombreCompleto'] ?? data['username']) as String,
      );
    } on ViewerException {
      rethrow;
    } catch (_) {
      throw const ViewerException(
        'No se pudo iniciar sesión. Revisa tu conexión.',
      );
    }
  }

  @override
  Future<List<ViewerProject>> projects(ViewerSession session) async {
    final data = await _read('/projects', session) as List;
    return data
        .map(
          (p) => ViewerProject(
            p['id'] as String,
            p['name'] as String,
            p['description'] as String? ?? '',
            p['memberRole'] as String? ?? 'VIEWER',
          ),
        )
        .toList();
  }

  String _project(String project) =>
      '/projects/${Uri.encodeComponent(project)}';
  @override
  Future<ViewerDiagram> diagram(ViewerSession session, String project) async {
    final data = await _read('${_project(project)}/diagram', session) as Map;
    final model = Map<String, dynamic>.from(data['model'] as Map);
    if (model['format'] != 'uml-class-diagram' ||
        model['classes'] is! List ||
        model['relations'] is! List) {
      throw const ViewerException(
        'El diagrama guardado tiene un formato incompatible.',
      );
    }
    return ViewerDiagram(project, (data['modelVersion'] as num).toInt(), model);
  }

  @override
  Future<ViewerPage<TimelineEntry>> activities(
    ViewerSession session,
    String project,
    String? cursor,
  ) async {
    final data =
        await _read('${_project(project)}/activities', session, {
              'limit': '30',
              if (cursor != null) 'cursor': cursor,
            })
            as Map;
    return ViewerPage(
      (data['items'] as List)
          .map(
            (v) => TimelineEntry(
              id: v['id'] as String,
              author: v['author'] as String,
              kind: v['kind'] as String,
              message: v['description'] as String,
              at: DateTime.fromMillisecondsSinceEpoch(
                (v['timestamp'] as num).toInt(),
              ),
            ),
          )
          .toList(),
      data['nextCursor'] as String?,
    );
  }

  @override
  Future<bool> conversationsEnabled(
    ViewerSession session,
    String project,
  ) async =>
      (await _read('${_project(project)}/ai/status', session)
          as Map)['enabled'] ==
      true;
  @override
  Future<ViewerPage<TimelineEntry>> conversations(
    ViewerSession session,
    String project,
    String? cursor,
  ) async {
    final data =
        await _read('${_project(project)}/ai/messages', session, {
              'limit': '30',
              if (cursor != null) 'cursor': cursor,
            })
            as Map;
    return ViewerPage(
      (data['turns'] as List).map((v) {
        final mode =
            v['mode'] == 'MODEL'
                ? 'Modelado con IA'
                : v['mode'] == 'REVIEW'
                ? 'Revisión con IA'
                : 'Consulta a IA';
        final state = v['state'];
        final answer =
            state == 'PENDING'
                ? 'En proceso…'
                : state == 'FAILED'
                ? 'La solicitud no pudo completarse.'
                : (v['answer']?['text'] ?? '') as String;
        return TimelineEntry(
          id: v['requestId'] as String,
          author: v['authorName'] as String,
          kind: mode,
          message: '${v['instruction']}\n\n$answer',
          at: DateTime.parse(v['updatedAt'] as String),
          ai: true,
        );
      }).toList(),
      data['nextCursor'] as String?,
    );
  }

  @override
  void close() => _client.close();
}
