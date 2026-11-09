import '../domain/project_models.dart';
import '../domain/project_viewer_port.dart';
import '../../project_notifications/application/project_notification_use_cases.dart';

class ProjectViewerUseCases {
  ProjectViewerUseCases(this.port, {this.notifications});
  final ProjectViewerPort port;
  final ProjectNotificationUseCases? notifications;
  Future<ViewerSession> login(String email, String password) {
    if (email.trim().isEmpty || password.isEmpty) {
      throw const ViewerException('Escribe tu correo y contraseña.');
    }
    return port.login(email.trim(), password);
  }

  Future<List<ViewerProject>> projects(ViewerSession session) =>
      port.projects(session);
  Future<ViewerDiagram> diagram(ViewerSession session, String project) =>
      port.diagram(session, project);

  Future<ViewerTimeline> timeline(
    ViewerSession session,
    String project, {
    String? activityCursor,
    String? aiCursor,
    bool loadActivity = true,
    bool loadAi = true,
  }) async {
    var human = const ViewerPage<TimelineEntry>([], null);
    var ai = const ViewerPage<TimelineEntry>([], null);
    final warnings = <String>[];
    if (loadActivity) {
      try {
        human = await port.activities(session, project, activityCursor);
      } on ViewerException catch (e) {
        _checkAccess(e);
        warnings.add(e.message);
      }
    }
    if (loadAi) {
      try {
        if (await port.conversationsEnabled(session, project)) {
          ai = await port.conversations(session, project, aiCursor);
        }
      } on ViewerException catch (e) {
        _checkAccess(e);
        warnings.add(e.message);
      }
    }
    return ViewerTimeline(
      merge([], [...human.items, ...ai.items]),
      human.cursor,
      ai.cursor,
      warnings,
    );
  }

  static List<TimelineEntry> merge(
    List<TimelineEntry> old,
    List<TimelineEntry> incoming,
  ) {
    final entries = {
      for (final e in old) e.key: e,
      for (final e in incoming) e.key: e,
    };
    return entries.values.toList()..sort((a, b) {
      final time = b.at.compareTo(a.at);
      return time == 0 ? b.key.compareTo(a.key) : time;
    });
  }

  void _checkAccess(ViewerException error) {
    if (const {401, 403, 404}.contains(error.status)) throw error;
  }

  void dispose() {
    notifications?.dispose();
    port.close();
  }
}
