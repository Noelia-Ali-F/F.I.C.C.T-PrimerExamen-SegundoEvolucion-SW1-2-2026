class AssistantApiResult {
  const AssistantApiResult({
    required this.message,
    required this.data,
    required this.statusCode,
  });

  final String message;
  final dynamic data;
  final int statusCode;
}

class AssistantApiException implements Exception {
  const AssistantApiException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;
  bool get isTemporary =>
      statusCode == 408 ||
      statusCode == 429 ||
      (statusCode != null && statusCode! >= 500);
  @override
  String toString() => message;
}

class AssistantTemporaryException extends AssistantApiException {
  const AssistantTemporaryException(super.message) : super(statusCode: 408);
}

class AssistantOfflineException extends AssistantApiException {
  const AssistantOfflineException()
    : super(
        'Sin conexión. La interpretación local funciona, pero esta operación necesita acceso al backend.',
      );
}
