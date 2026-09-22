enum AssistantIntent { create, query, update, delete }

class AssistantCommand {
  const AssistantCommand({
    required this.intent,
    required this.entity,
    this.entityId,
    this.lookupName,
    this.data = const {},
    required this.originalText,
  });

  final AssistantIntent intent;
  final String entity;
  final String? entityId;
  final String? lookupName;
  final Map<String, dynamic> data;
  final String originalText;

  bool get isDestructive => intent == AssistantIntent.delete;

  String get endpointName =>
      entity.trim().toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), '-');

  String get intentLabel => switch (intent) {
    AssistantIntent.create => 'REGISTRAR',
    AssistantIntent.query => 'CONSULTAR',
    AssistantIntent.update => 'ACTUALIZAR',
    AssistantIntent.delete => 'ELIMINAR',
  };

  Map<String, dynamic> toJson() => {
    'intent': intentLabel,
    'entity': entity,
    if (entityId != null) 'id': entityId,
    if (lookupName != null) 'lookupName': lookupName,
    if (data.isNotEmpty) 'data': data,
  };

  Map<String, dynamic> toStoredJson() => {
    'intent': intent.name,
    'entity': entity,
    'entityId': entityId,
    'lookupName': lookupName,
    'data': data,
    'originalText': originalText,
  };

  factory AssistantCommand.fromStoredJson(Map<String, dynamic> json) {
    return AssistantCommand(
      intent: AssistantIntent.values.byName(json['intent'] as String),
      entity: json['entity'] as String,
      entityId: json['entityId'] as String?,
      lookupName: json['lookupName'] as String?,
      data: Map<String, dynamic>.from(json['data'] as Map? ?? const {}),
      originalText: json['originalText'] as String? ?? '',
    );
  }
}

class AssistantInterpretation {
  const AssistantInterpretation.ready(this.command) : clarification = null;

  const AssistantInterpretation.clarification(this.clarification)
    : command = null;

  final AssistantCommand? command;
  final String? clarification;

  bool get isReady => command != null;
}
