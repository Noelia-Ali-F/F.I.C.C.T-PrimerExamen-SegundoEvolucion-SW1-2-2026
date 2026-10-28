import '../assistant_command.dart';
import '../assistant_api_result.dart';

abstract interface class BackendApiPort {
  Future<AssistantApiResult> execute(
    AssistantCommand command, {
    String? idempotencyKey,
  });
}
