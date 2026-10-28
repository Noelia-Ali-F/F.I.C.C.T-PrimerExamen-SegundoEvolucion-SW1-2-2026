import '../domain/ports/backend_api_port.dart';
import '../domain/assistant_api_result.dart';
import '../domain/assistant_command.dart';
import '../domain/local_intent_engine.dart';

class AssistantUseCases {
  const AssistantUseCases({
    required this.api,
    this.engine = const LocalIntentEngine(),
  });
  final BackendApiPort api;
  final LocalIntentEngine engine;
  AssistantInterpretation interpret(String text) => engine.interpret(text);
  Future<AssistantApiResult> execute(AssistantCommand command) =>
      api.execute(command);
}
