import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../../local_assistant/data/generated_backend_api.dart';
import '../../local_assistant/domain/assistant_command.dart';
import '../domain/pending_operation.dart';
import 'connectivity_service.dart';
import 'local_database_service.dart';

enum MobileConnectionStatus { online, offline, syncing }

class SyncService extends ChangeNotifier {
  static const maxAutomaticAttempts = 3;
  SyncService({
    required this.api,
    PendingOperationStore? database,
    ConnectivityService? connectivity,
    Uuid? uuid,
  }) : database = database ?? LocalDatabaseService(),
       _connectivity = connectivity ?? ConnectivityService(),
       _uuid = uuid ?? const Uuid();

  final GeneratedBackendApi api;
  final PendingOperationStore database;
  final ConnectivityService _connectivity;
  final Uuid _uuid;
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
      notifyListeners();
    }
  }

  Future<PendingOperation> enqueue(AssistantCommand command) async {
    final operation = PendingOperation(
      localId: _uuid.v4(),
      entity: command.entity,
      action: command.intentLabel,
      data: command.data,
      createdAt: DateTime.now().toUtc(),
      status: PendingOperationStatus.pending,
      command: command,
    );
    await database.saveOperation(operation);
    _operations = [operation, ..._operations];
    notifyListeners();
    return operation;
  }

  void markOffline() {
    if (_status == MobileConnectionStatus.syncing) return;
    _status = MobileConnectionStatus.offline;
    notifyListeners();
  }

  Future<void> synchronize() async {
    if (_status == MobileConnectionStatus.offline ||
        _status == MobileConnectionStatus.syncing) {
      return;
    }
    final candidates = pendingOperations;
    if (candidates.isEmpty) return;
    _status = MobileConnectionStatus.syncing;
    notifyListeners();

    var connectionLost = false;
    for (final operation in candidates) {
      if (_sending.contains(operation.localId) ||
          operation.status == PendingOperationStatus.synchronized) {
        continue;
      }
      _sending.add(operation.localId);
      final attempted = operation.copyWith(
        attemptCount: operation.attemptCount + 1,
        lastAttemptAt: DateTime.now().toUtc(),
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
            synchronizedAt: DateTime.now().toUtc(),
          ),
        );
      } on AssistantOfflineException catch (error) {
        connectionLost = true;
        await _replace(
          attempted.copyWith(
            status: attempted.attemptCount >= maxAutomaticAttempts
                ? PendingOperationStatus.error
                : PendingOperationStatus.pending,
            errorMessage: error.message,
          ),
        );
        break;
      } on AssistantApiException catch (error) {
        await _replace(
          attempted.copyWith(
            status: error.isTemporary &&
                    attempted.attemptCount < maxAutomaticAttempts
                ? PendingOperationStatus.pending
                : PendingOperationStatus.error,
            errorMessage: error.message,
          ),
        );
      } catch (_) {
        await _replace(
          attempted.copyWith(
            status: attempted.attemptCount < maxAutomaticAttempts
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
    notifyListeners();
  }

  Future<void> _handleConnectivity(bool connected) async {
    if (!connected) {
      _status = MobileConnectionStatus.offline;
      notifyListeners();
      return;
    }
    _status = MobileConnectionStatus.online;
    notifyListeners();
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
    notifyListeners();
  }

  @override
  void dispose() {
    _connectivitySubscription?.cancel();
    super.dispose();
  }
}
