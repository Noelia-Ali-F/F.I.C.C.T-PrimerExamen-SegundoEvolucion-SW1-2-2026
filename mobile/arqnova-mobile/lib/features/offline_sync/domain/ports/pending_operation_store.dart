import '../pending_operation.dart';

abstract interface class PendingOperationStore {
  Future<List<PendingOperation>> getOperations();
  Future<void> saveOperation(PendingOperation operation);
}
