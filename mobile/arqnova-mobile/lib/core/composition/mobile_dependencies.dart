import 'package:uuid/uuid.dart';
import '../../features/local_assistant/domain/ports/backend_api_port.dart';
import '../../features/local_assistant/domain/ports/speech_input_port.dart';
import '../../features/local_assistant/data/generated_backend_api.dart';
import '../../features/local_assistant/data/speech_to_text_adapter.dart';
import '../../features/offline_sync/data/local_database_service.dart';
import '../../features/offline_sync/data/connectivity_service.dart';
import '../../features/offline_sync/application/offline_sync_use_cases.dart';
import '../../features/offline_sync/presentation/sync_controller.dart';

class MobileDependencies {
  static SpeechInputPort createSpeechInput() => SpeechToTextAdapter();
  MobileDependencies._(this.api, this.sync, this.createSpeech);
  final BackendApiPort api;
  final SyncController sync;
  final SpeechInputPort Function() createSpeech;
  factory MobileDependencies.create() {
    final api = GeneratedBackendApi();
    final database = LocalDatabaseService();
    final connectivity = ConnectivityService();
    final sync = SyncController(
      (changed) => OfflineSyncUseCases(
        api: api,
        database: database,
        connectivity: connectivity,
        nextId: const Uuid().v4,
        now: DateTime.now,
        changed: changed,
      ),
    );
    return MobileDependencies._(api, sync, () => SpeechToTextAdapter());
  }
}
