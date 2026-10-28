class VoiceTranscript {
  const VoiceTranscript(this.recognizedWords, this.finalResult);
  final String recognizedWords;
  final bool finalResult;
}

class VoiceFailure {
  const VoiceFailure(this.permanent);
  final bool permanent;
}

abstract interface class SpeechInputPort {
  bool get isListening;
  Future<bool> initialize({
    required void Function(VoiceFailure) onError,
    required void Function(String) onStatus,
  });
  Future<void> listen({required void Function(VoiceTranscript) onResult});
  Future<void> stop();
  Future<void> cancel();
}
