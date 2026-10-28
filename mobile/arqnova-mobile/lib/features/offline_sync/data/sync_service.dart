// Compatibility composition entry point. The synchronization rules live in application/.
import 'package:uuid/uuid.dart';
import '../../local_assistant/domain/ports/backend_api_port.dart';
import '../domain/ports/pending_operation_store.dart';
import '../domain/ports/connectivity_port.dart';
import '../application/offline_sync_use_cases.dart';
import '../presentation/sync_controller.dart';
import 'local_database_service.dart' show LocalDatabaseService;
import 'connectivity_service.dart';
export '../domain/mobile_connection_status.dart';

class SyncService extends SyncController {
  static const maxAutomaticAttempts = OfflineSyncUseCases.maxAutomaticAttempts;
  SyncService({
    required BackendApiPort api,
    PendingOperationStore? database,
    ConnectivityPort? connectivity,
    Uuid? uuid,
  }) : super(
         (changed) => OfflineSyncUseCases(
           api: api,
           database: database ?? LocalDatabaseService(),
           connectivity: connectivity ?? ConnectivityService(),
           nextId: (uuid ?? const Uuid()).v4,
           now: DateTime.now,
           changed: changed,
         ),
       );
}
