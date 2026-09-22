import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:arqnova_mobile/features/local_assistant/data/generated_backend_api.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/assistant_command.dart';

void main() {
  test('envía POST al controlador real de la entidad', () async {
    late http.Request captured;
    final api = GeneratedBackendApi(
      client: MockClient((request) async {
        captured = request;
        return http.Response(jsonEncode({'id': 1, 'nombre': 'Ana'}), 201);
      }),
    );

    final result = await api.execute(
      const AssistantCommand(
        intent: AssistantIntent.create,
        entity: 'cliente',
        data: {'nombre': 'Ana'},
        originalText: 'Registrar cliente Ana',
      ),
    );

    expect(captured.method, 'POST');
    expect(captured.url.path, '/api/cliente');
    expect(jsonDecode(captured.body), {'nombre': 'Ana'});
    expect(result.statusCode, 201);
  });

  test('consulta lista mediante GET', () async {
    final api = GeneratedBackendApi(
      client: MockClient((request) async => http.Response('[]', 200)),
    );

    final result = await api.execute(
      const AssistantCommand(
        intent: AssistantIntent.query,
        entity: 'cliente',
        originalText: 'Listar clientes',
      ),
    );

    expect(result.data, isEmpty);
  });

  test('clasifica 503 como error temporal', () async {
    final api = GeneratedBackendApi(
      client: MockClient(
        (_) async => http.Response(jsonEncode({'message': 'No disponible'}), 503),
      ),
    );

    await expectLater(
      api.execute(
        const AssistantCommand(
          intent: AssistantIntent.create,
          entity: 'cliente',
          data: {'nombre': 'Ana'},
          originalText: 'Registrar cliente Ana',
        ),
      ),
      throwsA(
        isA<AssistantApiException>()
            .having((error) => error.statusCode, 'statusCode', 503)
            .having((error) => error.isTemporary, 'isTemporary', isTrue),
      ),
    );
  });

  test('clasifica 422 como rechazo permanente', () async {
    final api = GeneratedBackendApi(
      client: MockClient(
        (_) async => http.Response(jsonEncode({'message': 'Dato inválido'}), 422),
      ),
    );

    await expectLater(
      api.execute(
        const AssistantCommand(
          intent: AssistantIntent.create,
          entity: 'cliente',
          data: {'nombre': ''},
          originalText: 'Registrar cliente',
        ),
      ),
      throwsA(
        isA<AssistantApiException>()
            .having((error) => error.statusCode, 'statusCode', 422)
            .having((error) => error.isTemporary, 'isTemporary', isFalse),
      ),
    );
  });
}
