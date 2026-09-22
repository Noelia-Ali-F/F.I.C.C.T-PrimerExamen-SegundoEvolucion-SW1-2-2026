import '../../local_assistant/domain/assistant_command.dart';

enum PendingOperationStatus { pending, synchronized, error }

class PendingOperation {
  const PendingOperation({
    required this.localId,
    required this.entity,
    required this.action,
    required this.data,
    required this.createdAt,
    required this.status,
    required this.command,
    this.serverData,
    this.errorMessage,
    this.attemptCount = 0,
    this.lastAttemptAt,
    this.synchronizedAt,
  });

  final String localId;
  final String entity;
  final String action;
  final Map<String, dynamic> data;
  final DateTime createdAt;
  final PendingOperationStatus status;
  final AssistantCommand command;
  final dynamic serverData;
  final String? errorMessage;
  final int attemptCount;
  final DateTime? lastAttemptAt;
  final DateTime? synchronizedAt;

  PendingOperation copyWith({
    PendingOperationStatus? status,
    dynamic serverData,
    String? errorMessage,
    bool clearError = false,
    int? attemptCount,
    DateTime? lastAttemptAt,
    DateTime? synchronizedAt,
  }) => PendingOperation(
    localId: localId,
    entity: entity,
    action: action,
    data: data,
    createdAt: createdAt,
    status: status ?? this.status,
    command: command,
    serverData: serverData ?? this.serverData,
    errorMessage: clearError ? null : errorMessage ?? this.errorMessage,
    attemptCount: attemptCount ?? this.attemptCount,
    lastAttemptAt: lastAttemptAt ?? this.lastAttemptAt,
    synchronizedAt: synchronizedAt ?? this.synchronizedAt,
  );
}
