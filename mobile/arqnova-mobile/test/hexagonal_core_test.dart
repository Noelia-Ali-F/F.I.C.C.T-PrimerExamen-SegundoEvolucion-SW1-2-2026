import 'dart:async';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/assistant_command.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/assistant_api_result.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/ports/backend_api_port.dart';
import 'package:arqnova_mobile/features/local_assistant/application/assistant_use_cases.dart';
import 'package:arqnova_mobile/features/offline_sync/application/offline_sync_use_cases.dart';
import 'package:arqnova_mobile/features/offline_sync/domain/pending_operation.dart';
import 'package:arqnova_mobile/features/offline_sync/domain/mobile_connection_status.dart';
import 'package:arqnova_mobile/features/offline_sync/domain/ports/pending_operation_store.dart';
import 'package:arqnova_mobile/features/offline_sync/domain/ports/connectivity_port.dart';

void main() {
  test(
    'el núcleo offline funciona con puertos de memoria sin plugins',
    () async {
      final api = _Backend();
      final storage = _Store();
      final network = _Network();
      var changes = 0;
      final now = DateTime.utc(2026, 10, 28);
      final core = OfflineSyncUseCases(
        api: api,
        database: storage,
        connectivity: network,
        nextId: () => 'op-estable',
        now: () => now,
        changed: () => changes++,
      );
      await core.initialize();
      expect(core.status, MobileConnectionStatus.offline);
      final command = const AssistantCommand(
        intent: AssistantIntent.create,
        entity: 'cliente',
        originalText: 'registrar cliente Juan',
        data: {'nombre': 'Juan'},
      );
      final operation = await core.enqueue(command);
      expect(operation.localId, 'op-estable');
      expect(operation.createdAt, now);
      expect(api.requests, 0);
      network.goOnline();
      for (
        var i = 0;
        i < 50 &&
            core.operations.single.status !=
                PendingOperationStatus.synchronized;
        i++
      ) {
        await Future<void>.delayed(Duration.zero);
      }
      expect(api.requests, 1);
      expect(api.lastKey, 'op-estable');
      expect(
        core.operations.single.status,
        PendingOperationStatus.synchronized,
      );
      expect(changes, greaterThan(0));
      core.dispose();
      await network.controller.close();
    },
  );
  test(
    'el asistente interpreta localmente y ejecuta mediante el puerto',
    () async {
      final api = _Backend();
      final useCases = AssistantUseCases(api: api);
      final interpretation = useCases.interpret('buscar clientes');
      expect(interpretation.command, isNotNull);
      await useCases.execute(interpretation.command!);
      expect(api.requests, 1);
    },
  );
  test('dominio y aplicación no importan adaptadores ni frameworks', () {
    var count = 0;
    for (final file
        in Directory(
          'lib/features',
        ).listSync(recursive: true).whereType<File>()) {
      final path = file.uri.path;
      if (!path.endsWith('.dart') ||
          (!path.contains('/domain/') && !path.contains('/application/'))) {
        continue;
      }
      count++;
      final source = file.readAsStringSync();
      for (final forbidden in [
        'package:flutter/',
        'package:http/',
        'package:sqflite/',
        'package:connectivity_plus/',
        'package:speech_to_text/',
        'package:uuid/',
      ]) {
        expect(source, isNot(contains(forbidden)), reason: path);
      }
      final imports = RegExp(
        r"""(?:import|export)\s+['"]([^'"]+)['"]""",
      ).allMatches(source);
      for (final item in imports) {
        expect(item.group(1)!.contains('/data/'), isFalse, reason: path);
      }
    }
    expect(count, greaterThan(5));
  });
}

class _Backend implements BackendApiPort {
  int requests = 0;
  String? lastKey;
  @override
  Future<AssistantApiResult> execute(
    AssistantCommand command, {
    String? idempotencyKey,
  }) async {
    requests++;
    lastKey = idempotencyKey;
    return const AssistantApiResult(
      message: 'ok',
      data: {'id': 1},
      statusCode: 201,
    );
  }
}

class _Store implements PendingOperationStore {
  final values = <String, PendingOperation>{};
  @override
  Future<List<PendingOperation>> getOperations() async =>
      values.values.toList();
  @override
  Future<void> saveOperation(PendingOperation operation) async {
    values[operation.localId] = operation;
  }
}

class _Network implements ConnectivityPort {
  final controller = StreamController<bool>.broadcast(sync: true);
  bool connected = false;
  @override
  Stream<bool> get changes => controller.stream;
  @override
  Future<bool> isConnected() async => connected;
  void goOnline() {
    connected = true;
    controller.add(true);
  }
}
