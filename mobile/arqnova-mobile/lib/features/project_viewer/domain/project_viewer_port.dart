import 'project_models.dart';

/// Deliberately exposes no diagram mutation or assistant execution operations.
abstract interface class ProjectViewerPort {
  Future<ViewerSession> login(String username, String password);
  Future<List<ViewerProject>> projects(ViewerSession session);
  Future<ViewerDiagram> diagram(ViewerSession session, String project);
  Future<ViewerPage<TimelineEntry>> activities(
    ViewerSession session,
    String project,
    String? cursor,
  );
  Future<bool> conversationsEnabled(ViewerSession session, String project);
  Future<ViewerPage<TimelineEntry>> conversations(
    ViewerSession session,
    String project,
    String? cursor,
  );
  void close();
}
