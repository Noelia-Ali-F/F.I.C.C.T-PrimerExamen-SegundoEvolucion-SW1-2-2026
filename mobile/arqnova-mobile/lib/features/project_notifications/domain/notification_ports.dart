class ProjectNotification {
  const ProjectNotification(this.projectId, this.title, this.body);
  final String projectId;
  final String title;
  final String body;
}

class NotificationException implements Exception {
  const NotificationException(this.message);
  final String message;
  @override
  String toString() => message;
}

abstract interface class PushPlatformPort {
  Future<bool> initialize();
  Future<bool> permissionGranted();
  Future<bool> requestPermission();
  Future<String> register();
  Future<void> unregister();
  Stream<String> get registrations;
  Stream<ProjectNotification> get messages;
  Stream<ProjectNotification> get opened;
  ProjectNotification? takePending();
}

abstract interface class PushDeviceApiPort {
  Future<bool> enabled(String authToken);
  Future<void> register(String authToken, String fid);
  Future<void> remove(String authToken, String fid);
  void close();
}
