import 'dart:async';
import '../../local_assistant/domain/ports/backend_api_port.dart';
import '../../local_assistant/domain/assistant_api_result.dart';
import '../../local_assistant/domain/assistant_command.dart';
import '../domain/pending_operation.dart';
import '../domain/mobile_connection_status.dart';
import '../domain/ports/pending_operation_store.dart';
import '../domain/ports/connectivity_port.dart';

class OfflineSyncUseCases {
  static const maxAutomaticAttempts = 3;
  OfflineSyncUseCases({
    required this.api,
    required this.database,
    required ConnectivityPort connectivity,
    required String Function() nextId,
    required DateTime Function() now,
    required void Function() changed,
  }) : _connectivity = connectivity,
       _nextId = nextId,
       _now = now,
       _changed = changed;
  final BackendApiPort api;
  final PendingOperationStore database;
  final ConnectivityPort _connectivity;
  final String Function() _nextId;
  final DateTime Function() _now;
  final void Function() _changed;
  final Set<String> _sending = {};
  StreamSubscription<bool>? _connectivitySubscription;
  List<PendingOperation> _operations = [];
  MobileConnectionStatus _status = MobileConnectionStatus.online;
  bool _initialized = false;

  List<PendingOperation> get operations => List.unmodifiable(_operations);
  List<PendingOperation> get pendingOperations => _operations
      .where((operation) => operation.status == PendingOperationStatus.pending)
      .toList(growable: false);
  int get pendingCount => pendingOperations.length;
  MobileConnectionStatus get status => _status;
  bool get isOffline => _status == MobileConnectionStatus.offline;

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    try {
      _operations = await database.getOperations();
      _connectivitySubscription = _connectivity.changes.listen(
        _handleConnectivity,
      );
      await _handleConnectivity(await _connectivity.isConnected());
    } catch (_) {
      _status = MobileConnectionStatus.offline;
      _changed();
    }
  }

  Future<PendingOperation> enqueue(AssistantCommand command) async {
    final operation = PendingOperation(
      localId: _nextId(),
      entity: command.entity,
      action: command.intentLabel,
      data: command.data,
      createdAt: _now().toUtc(),
      status: PendingOperationStatus.pending,
      command: command,
    );
    await database.saveOperation(operation);
    _operations = [operation, ..._operations];
    _changed();
    return operation;
  }

  void markOffline() {
    if (_status == MobileConnectionStatus.syncing) return;
    _status = MobileConnectionStatus.offline;
    _changed();
  }

  Future<void> synchronize() async {
    if (_status == MobileConnectionStatus.offline ||
        _status == MobileConnectionStatus.syncing) {
      return;
    }
    final candidates = pendingOperations;
    if (candidates.isEmpty) return;
    _status = MobileConnectionStatus.syncing;
    _changed();

    var connectionLost = false;
    for (final operation in candidates) {
      if (_sending.contains(operation.localId) ||
          operation.status == PendingOperationStatus.synchronized) {
        continue;
      }
      _sending.add(operation.localId);
      final attempted = operation.copyWith(
        attemptCount: operation.attemptCount + 1,
        lastAttemptAt: _now().toUtc(),
      );
      await _replace(attempted);
      try {
        final result = await api.execute(
          attempted.command,
          idempotencyKey: attempted.localId,
        );
        await _replace(
          attempted.copyWith(
            status: PendingOperationStatus.synchronized,
            serverData: result.data,
            clearError: true,
            synchronizedAt: _now().toUtc(),
          ),
        );
      } on AssistantOfflineException catch (error) {
        connectionLost = true;
        await _replace(
          attempted.copyWith(
            status:
                attempted.attemptCount >= maxAutomaticAttempts
                    ? PendingOperationStatus.error
                    : PendingOperationStatus.pending,
            errorMessage: error.message,
          ),
        );
        break;
      } on AssistantApiException catch (error) {
        await _replace(
          attempted.copyWith(
            status:
                error.isTemporary &&
                        attempted.attemptCount < maxAutomaticAttempts
                    ? PendingOperationStatus.pending
                    : PendingOperationStatus.error,
            errorMessage: error.message,
          ),
        );
      } catch (_) {
        await _replace(
          attempted.copyWith(
            status:
                attempted.attemptCount < maxAutomaticAttempts
                    ? PendingOperationStatus.pending
                    : PendingOperationStatus.error,
            errorMessage: 'No se pudo sincronizar la operación.',
          ),
        );
      } finally {
        _sending.remove(operation.localId);
      }
    }

    _status =
        connectionLost
            ? MobileConnectionStatus.offline
            : MobileConnectionStatus.online;
    _changed();
  }

  Future<void> _handleConnectivity(bool connected) async {
    if (!connected) {
      _status = MobileConnectionStatus.offline;
      _changed();
      return;
    }
    _status = MobileConnectionStatus.online;
    _changed();
    await synchronize();
  }

  Future<void> _replace(PendingOperation updated) async {
    await database.saveOperation(updated);
    _operations =
        _operations
            .map(
              (operation) =>
                  operation.localId == updated.localId ? updated : operation,
            )
            .toList();
    _changed();
  }

  void dispose() {
    _connectivitySubscription?.cancel();
  }
}
