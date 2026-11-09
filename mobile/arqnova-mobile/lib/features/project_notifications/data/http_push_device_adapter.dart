import 'dart:convert';
import 'package:http/http.dart' as http;
import '../domain/notification_ports.dart';

class HttpPushDeviceAdapter implements PushDeviceApiPort {
  HttpPushDeviceAdapter({
    http.Client? client,
    this.baseUrl = const String.fromEnvironment(
      'UML_API_BASE_URL',
      defaultValue: 'http://10.0.2.2:8080',
    ),
  }) : _client = client ?? http.Client();
  final http.Client _client;
  final String baseUrl;
  Uri _uri(String path) =>
      Uri.parse('${baseUrl.replaceAll(RegExp(r'/+$'), '')}/api/v1/push$path');
  @override
  Future<bool> enabled(String token) async {
    try {
      final response = await _client
          .get(_uri('/status'), headers: {'Authorization': 'Bearer $token'})
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) return false;
      return jsonDecode(response.body)['datos']?['enabled'] == true;
    } catch (_) {
      return false;
    }
  }

  Future<void> _post(String path, String token, String fid) async {
    try {
      final response = await _client
          .post(
            _uri(path),
            headers: {
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $token',
            },
            body: jsonEncode({'fid': fid}),
          )
          .timeout(const Duration(seconds: 15));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw const NotificationException(
          'No se pudo actualizar el dispositivo de notificaciones.',
        );
      }
    } on NotificationException {
      rethrow;
    } catch (_) {
      throw const NotificationException(
        'No se pudo conectar con el servicio de notificaciones.',
      );
    }
  }

  @override
  Future<void> register(String authToken, String fid) =>
      _post('/devices', authToken, fid);
  @override
  Future<void> remove(String authToken, String fid) =>
      _post('/devices/remove', authToken, fid);
  @override
  void close() => _client.close();
}
