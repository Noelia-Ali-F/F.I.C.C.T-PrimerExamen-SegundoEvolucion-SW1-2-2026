import 'dart:async';
import 'dart:convert';

import 'package:arqnova_mobile/features/local_assistant/data/generated_backend_api.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/assistant_command.dart';
import 'package:arqnova_mobile/features/offline_sync/data/connectivity_service.dart';
import 'package:arqnova_mobile/features/offline_sync/data/local_database_service.dart';
import 'package:arqnova_mobile/features/offline_sync/data/sync_service.dart';
import 'package:arqnova_mobile/features/offline_sync/domain/pending_operation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test(
    'guarda offline y sincroniza una sola vez al recuperar conexión',
    () async {
      final store = _MemoryStore();
      final connectivity = _FakeConnectivity(false);
      var requests = 0;
      String? idempotencyKey;
      final api = GeneratedBackendApi(
        client: MockClient((request) async {
          requests++;
          idempotencyKey = request.headers['Idempotency-Key'];
          return http.Response(jsonEncode({'id': 10, 'nombre': 'Juan'}), 201);
        }),
      );
      final service = SyncService(
        api: api,
        database: store,
        connectivity: connectivity,
      );
      final observedStatuses = <MobileConnectionStatus>[];
      service.addListener(() => observedStatuses.add(service.status));
      await service.initialize();
      expect(service.status, MobileConnectionStatus.offline);

      final operation = await service.enqueue(_createCommand);
      expect(operation.status, PendingOperationStatus.pending);
      expect(store.operations, hasLength(1));

      connectivity.setConnected(true);
      await _eventually(
        () =>
            service.operations.single.status ==
            PendingOperationStatus.synchronized,
      );
      expect(requests, 1);
      expect(idempotencyKey, operation.localId);
      expect(observedStatuses, contains(MobileConnectionStatus.syncing));
      expect(service.operations.single.attemptCount, 1);
      expect(service.operations.single.lastAttemptAt, isNotNull);
      expect(service.operations.single.synchronizedAt, isNotNull);

      await service.synchronize();
      expect(requests, 1, reason: 'una operación sincronizada no se reenvía');
      service.dispose();
    },
  );

  test('conserva la operación con ERROR si el backend la rechaza', () async {
    final store = _MemoryStore();
    final connectivity = _FakeConnectivity(true);
    final api = GeneratedBackendApi(
      client: MockClient(
        (_) async =>
            http.Response(jsonEncode({'message': 'Dato inválido'}), 422),
      ),
    );
    final service = SyncService(
      api: api,
      database: store,
      connectivity: connectivity,
    );
    await service.initialize();
    await service.enqueue(_createCommand);
    await service.synchronize();

    expect(service.operations.single.status, PendingOperationStatus.error);
    expect(service.operations.single.errorMessage, 'Dato inválido');
    expect(service.operations.single.attemptCount, 1);
    expect(store.operations, hasLength(1));
    await service.synchronize();
    expect(service.operations.single.attemptCount, 1,
        reason: 'los rechazos permanentes no se reintentan automáticamente');
    service.dispose();
  });

  test('reintenta errores temporales hasta el límite y luego los detiene', () async {
    final store = _MemoryStore();
    final connectivity = _FakeConnectivity(true);
    var requests = 0;
    final api = GeneratedBackendApi(
      client: MockClient((_) async {
        requests++;
        return http.Response(jsonEncode({'message': 'Servicio no disponible'}), 503);
      }),
    );
    final service = SyncService(api: api, database: store, connectivity: connectivity);
    await service.initialize();
    await service.enqueue(_createCommand);

    await service.synchronize();
    await service.synchronize();
    await service.synchronize();
    await service.synchronize();

    expect(requests, SyncService.maxAutomaticAttempts);
    expect(service.operations.single.status, PendingOperationStatus.error);
    expect(service.operations.single.attemptCount, SyncService.maxAutomaticAttempts);
    expect(service.pendingCount, 0);
    service.dispose();
  });
}

const _createCommand = AssistantCommand(
  intent: AssistantIntent.create,
  entity: 'cliente',
  data: {'nombre': 'Juan Pérez', 'telefono': '70000000'},
  originalText: 'Registrar cliente Juan Pérez con teléfono 70000000',
);

class _MemoryStore implements PendingOperationStore {
  final List<PendingOperation> operations = [];

  @override
  Future<List<PendingOperation>> getOperations() async => [...operations];

  @override
  Future<void> saveOperation(PendingOperation operation) async {
    final index = operations.indexWhere(
      (item) => item.localId == operation.localId,
    );
    if (index < 0) {
      operations.add(operation);
    } else {
      operations[index] = operation;
    }
  }
}

class _FakeConnectivity extends ConnectivityService {
  _FakeConnectivity(this.connected);
  bool connected;
  final controller = StreamController<bool>.broadcast();

  @override
  Stream<bool> get changes => controller.stream;

  @override
  Future<bool> isConnected() async => connected;

  void setConnected(bool value) {
    connected = value;
    controller.add(value);
  }
}

Future<void> _eventually(bool Function() condition) async {
  for (var attempt = 0; attempt < 20; attempt++) {
    if (condition()) return;
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
  fail('La condición esperada no se alcanzó.');
}
