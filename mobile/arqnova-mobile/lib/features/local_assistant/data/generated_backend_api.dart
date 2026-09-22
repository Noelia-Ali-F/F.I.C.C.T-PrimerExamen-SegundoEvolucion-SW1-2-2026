import 'dart:convert';
import 'dart:io';
import 'dart:async';

import 'package:http/http.dart' as http;

import '../../../core/config/app_config.dart';
import '../domain/assistant_command.dart';

class GeneratedBackendApi {
  GeneratedBackendApi({http.Client? client})
    : _client = client ?? http.Client();

  final http.Client _client;
  static const requestTimeout = Duration(seconds: 15);

  Future<AssistantApiResult> execute(
    AssistantCommand command, {
    String? idempotencyKey,
  }) async {
    try {
      final resolved = await _resolveTarget(command);
      final path = '/api/${command.endpointName}';
      final headers = {
        'Content-Type': 'application/json',
        if (idempotencyKey != null) 'Idempotency-Key': idempotencyKey,
      };
      late http.Response response;

      switch (command.intent) {
        case AssistantIntent.query:
          response = await _client
              .get(AppConfig.apiUri(resolved == null ? path : '$path/$resolved'))
              .timeout(requestTimeout);
          break;
        case AssistantIntent.create:
          response = await _client
              .post(
                AppConfig.apiUri(path),
                headers: headers,
                body: jsonEncode(command.data),
              )
              .timeout(requestTimeout);
          break;
        case AssistantIntent.update:
          if (resolved == null) {
            throw const AssistantApiException(
              'No se encontró un registro único para actualizar.',
            );
          }
          final current = await _fetchOne(path, resolved);
          response = await _client
              .put(
                AppConfig.apiUri('$path/$resolved'),
                headers: headers,
                body: jsonEncode({...current, ...command.data}),
              )
              .timeout(requestTimeout);
          break;
        case AssistantIntent.delete:
          if (resolved == null) {
            throw const AssistantApiException(
              'No se encontró un registro único para eliminar.',
            );
          }
          response = await _client
              .delete(
                AppConfig.apiUri('$path/$resolved'),
                headers: headers,
              )
              .timeout(requestTimeout);
          break;
      }

      if (response.statusCode >= 400) {
        throw AssistantApiException(
          _errorMessage(response),
          statusCode: response.statusCode,
        );
      }
      final body = _decode(response.body);
      return AssistantApiResult(
        message: _successMessage(command),
        data: _unwrap(body),
        statusCode: response.statusCode,
      );
    } on SocketException {
      throw const AssistantOfflineException();
    } on http.ClientException {
      throw const AssistantOfflineException();
    } on TimeoutException {
      throw const AssistantTemporaryException(
        'El servidor tardó demasiado en responder. Inténtalo nuevamente.',
      );
    }
  }

  Future<String?> _resolveTarget(AssistantCommand command) async {
    if (command.entityId != null) return command.entityId;
    if (command.lookupName == null) return null;

    final path = '/api/${command.endpointName}';
    final response = await _client
        .get(AppConfig.apiUri(path))
        .timeout(requestTimeout);
    if (response.statusCode >= 400) {
      throw AssistantApiException(
        _errorMessage(response),
        statusCode: response.statusCode,
      );
    }
    final decoded = _unwrap(_decode(response.body));
    if (decoded is! List) return null;
    final expected = _normalize(command.lookupName!);
    final matches =
        decoded.whereType<Map>().where((item) {
          final name = item['nombre'] ?? item['name'] ?? item['titulo'];
          return name != null && _normalize(name.toString()) == expected;
        }).toList();
    if (matches.length != 1) {
      throw AssistantApiException(
        matches.isEmpty
            ? 'No encontré ${command.lookupName} en ${command.entity}.'
            : 'Encontré más de un registro con ese nombre. Indica el id.',
      );
    }
    final id = matches.single['id'];
    return id?.toString();
  }

  Future<Map<String, dynamic>> _fetchOne(String path, String id) async {
    final response = await _client
        .get(AppConfig.apiUri('$path/$id'))
        .timeout(requestTimeout);
    if (response.statusCode >= 400) {
      throw AssistantApiException(
        _errorMessage(response),
        statusCode: response.statusCode,
      );
    }
    final value = _unwrap(_decode(response.body));
    return value is Map<String, dynamic> ? value : <String, dynamic>{};
  }

  dynamic _decode(String body) {
    if (body.trim().isEmpty) return null;
    try {
      return jsonDecode(body);
    } on FormatException {
      return body;
    }
  }

  dynamic _unwrap(dynamic value) {
    if (value is Map<String, dynamic>) {
      return value['datos'] ?? value['data'] ?? value;
    }
    return value;
  }

  String _errorMessage(http.Response response) {
    final body = _decode(response.body);
    if (body is Map<String, dynamic>) {
      final message = body['mensaje'] ?? body['message'] ?? body['error'];
      if (message != null && message.toString().trim().isNotEmpty) {
        return message.toString();
      }
    }
    return 'El backend rechazó la operación (${response.statusCode}).';
  }

  String _successMessage(AssistantCommand command) => switch (command.intent) {
    AssistantIntent.create => '${command.entity} registrado correctamente.',
    AssistantIntent.query => 'Consulta completada.',
    AssistantIntent.update => '${command.entity} actualizado correctamente.',
    AssistantIntent.delete => '${command.entity} eliminado correctamente.',
  };

  String _normalize(String value) =>
      value
          .toLowerCase()
          .replaceAll('á', 'a')
          .replaceAll('é', 'e')
          .replaceAll('í', 'i')
          .replaceAll('ó', 'o')
          .replaceAll('ú', 'u')
          .trim();
}

class AssistantApiResult {
  const AssistantApiResult({
    required this.message,
    required this.data,
    required this.statusCode,
  });

  final String message;
  final dynamic data;
  final int statusCode;
}

class AssistantApiException implements Exception {
  const AssistantApiException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;
  bool get isTemporary =>
      statusCode == 408 ||
      statusCode == 429 ||
      (statusCode != null && statusCode! >= 500);
  @override
  String toString() => message;
}

class AssistantTemporaryException extends AssistantApiException {
  const AssistantTemporaryException(super.message) : super(statusCode: 408);
}

class AssistantOfflineException extends AssistantApiException {
  const AssistantOfflineException()
    : super(
        'Sin conexión. La interpretación local funciona, pero esta operación necesita acceso al backend.',
      );
}
