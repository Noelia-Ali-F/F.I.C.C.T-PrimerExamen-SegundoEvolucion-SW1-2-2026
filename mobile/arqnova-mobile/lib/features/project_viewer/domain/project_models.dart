class ViewerSession {
  const ViewerSession(this.token, this.username, this.name);
  final String token;
  final String username;
  final String name;
}

class ViewerProject {
  const ViewerProject(this.id, this.name, this.description, this.role);
  final String id;
  final String name;
  final String description;
  final String role;
}

class ViewerDiagram {
  const ViewerDiagram(this.projectId, this.version, this.model);
  final String projectId;
  final int version;
  final Map<String, dynamic> model;
}

class TimelineEntry {
  const TimelineEntry({
    required this.id,
    required this.author,
    required this.kind,
    required this.message,
    required this.at,
    this.ai = false,
  });
  final String id;
  final String author;
  final String kind;
  final String message;
  final DateTime at;
  final bool ai;
  String get key => '${ai ? 'ai' : 'user'}:$id';
}

class ViewerPage<T> {
  const ViewerPage(this.items, this.cursor);
  final List<T> items;
  final String? cursor;
}

class ViewerTimeline {
  const ViewerTimeline(
    this.entries,
    this.activityCursor,
    this.aiCursor,
    this.warnings,
  );
  final List<TimelineEntry> entries;
  final String? activityCursor;
  final String? aiCursor;
  final List<String> warnings;
}

class ViewerException implements Exception {
  const ViewerException(this.message, [this.status = 0]);
  final String message;
  final int status;
  @override
  String toString() => message;
}
