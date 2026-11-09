import 'package:uuid/uuid.dart';
import '../../features/project_viewer/application/project_viewer_use_cases.dart';
import '../../features/project_viewer/data/http_project_viewer_adapter.dart';
import '../../features/project_notifications/application/project_notification_use_cases.dart';
import '../../features/project_notifications/data/firebase_push_adapter.dart';
import '../../features/project_notifications/data/http_push_device_adapter.dart';
import '../../features/local_assistant/domain/ports/backend_api_port.dart';
import '../../features/local_assistant/domain/ports/speech_input_port.dart';
import '../../features/local_assistant/data/generated_backend_api.dart';
import '../../features/local_assistant/data/speech_to_text_adapter.dart';
import '../../features/offline_sync/data/local_database_service.dart';
import '../../features/offline_sync/data/connectivity_service.dart';
import '../../features/offline_sync/application/offline_sync_use_cases.dart';
import '../../features/offline_sync/presentation/sync_controller.dart';

class MobileDependencies {
  static final _firebase = FirebasePushAdapter();
  static Future<bool> initializeProjectNotifications() =>
      _firebase.initialize();
  static ProjectViewerUseCases createProjectViewer() => ProjectViewerUseCases(
    HttpProjectViewerAdapter(),
    notifications: ProjectNotificationUseCases(
      _firebase,
      HttpPushDeviceAdapter(),
    ),
  );
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
