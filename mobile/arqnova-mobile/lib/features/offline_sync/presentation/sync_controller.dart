import 'package:flutter/foundation.dart';
import '../../local_assistant/domain/ports/backend_api_port.dart';
import '../../local_assistant/domain/assistant_command.dart';
import '../domain/pending_operation.dart';
import '../domain/ports/pending_operation_store.dart';
import '../domain/mobile_connection_status.dart';
import '../application/offline_sync_use_cases.dart';
export '../domain/mobile_connection_status.dart';

class SyncController extends ChangeNotifier {
  static const maxAutomaticAttempts = OfflineSyncUseCases.maxAutomaticAttempts;
  SyncController(OfflineSyncUseCases Function(void Function()) create) {
    _core = create(notifyListeners);
  }
  late final OfflineSyncUseCases _core;
  BackendApiPort get api => _core.api;
  PendingOperationStore get database => _core.database;
  List<PendingOperation> get operations => _core.operations;
  List<PendingOperation> get pendingOperations => _core.pendingOperations;
  int get pendingCount => _core.pendingCount;
  MobileConnectionStatus get status => _core.status;
  bool get isOffline => _core.isOffline;
  Future<void> initialize() => _core.initialize();
  Future<PendingOperation> enqueue(AssistantCommand command) =>
      _core.enqueue(command);
  void markOffline() => _core.markOffline();
  Future<void> synchronize() => _core.synchronize();
  @override
  void dispose() {
    _core.dispose();
    super.dispose();
  }
}
