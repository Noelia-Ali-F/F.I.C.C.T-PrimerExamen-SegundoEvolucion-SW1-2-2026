import 'package:speech_to_text/speech_to_text.dart';
import '../domain/ports/speech_input_port.dart';

class SpeechToTextAdapter implements SpeechInputPort {
  SpeechToTextAdapter({SpeechToText? speech})
    : _speech = speech ?? SpeechToText();
  final SpeechToText _speech;
  @override
  bool get isListening => _speech.isListening;
  @override
  Future<bool> initialize({
    required void Function(VoiceFailure) onError,
    required void Function(String) onStatus,
  }) => _speech.initialize(
    onError: (error) => onError(VoiceFailure(error.permanent)),
    onStatus: onStatus,
  );
  @override
  Future<void> listen({required void Function(VoiceTranscript) onResult}) =>
      _speech.listen(
        onResult:
            (result) => onResult(
              VoiceTranscript(result.recognizedWords, result.finalResult),
            ),
        listenOptions: SpeechListenOptions(
          localeId: 'es_BO',
          partialResults: true,
        ),
      );
  @override
  Future<void> stop() => _speech.stop();
  @override
  Future<void> cancel() => _speech.cancel();
}
