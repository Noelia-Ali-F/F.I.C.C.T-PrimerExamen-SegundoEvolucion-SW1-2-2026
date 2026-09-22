import { TestBed } from '@angular/core/testing';
import { VoiceRecognitionService } from './voice-recognition.service';

class FakeSpeechRecognition {
  continuous = false;
  interimResults = false;
  lang = '';
  onstart?: () => void;
  onresult?: (event: any) => void;
  onerror?: (event: any) => void;
  onend?: () => void;
  startCalls = 0;
  stopCalls = 0;
  start() { this.startCalls++; this.onstart?.(); }
  stop() { this.stopCalls++; }
  emit(results: Array<{ text: string; final: boolean }>) {
    this.onresult?.({ results: results.map(value => Object.assign([{ transcript: value.text }], { isFinal: value.final })) });
  }
}

describe('VoiceRecognitionService', () => {
  let service: VoiceRecognitionService;
  let recognition: FakeSpeechRecognition;

  beforeEach(() => {
    (window as any).webkitSpeechRecognition = FakeSpeechRecognition;
    TestBed.configureTestingModule({ providers: [VoiceRecognitionService] });
    service = TestBed.inject(VoiceRecognitionService);
    recognition = service.recognition as FakeSpeechRecognition;
  });

  afterEach(() => { delete (window as any).webkitSpeechRecognition; });

  it('publica y detiene una sola vez al recibir resultado final', () => {
    service.start(true);
    recognition.emit([{ text: 'Crea una clase Mascota', final: true }]);
    const result = service.finalResult();
    recognition.onend?.();

    expect(result?.text).toBe('Crea una clase Mascota');
    expect(service.finalResult()?.id).toBe(result?.id);
    expect(service.isListening()).toBe(false);
    expect(recognition.stopCalls).toBe(1);
  });

  it('muestra interim pero no lo publica hasta detener manualmente', () => {
    service.start(true);
    recognition.emit([{ text: 'Agrega a Mascota un atributo nombre', final: false }]);
    expect(service.getInterim()).toContain('Agrega a Mascota');
    expect(service.finalResult()).toBeNull();

    expect(service.stop(true)).toBe(true);
    expect(service.finalResult()?.text).toContain('Agrega a Mascota');
    const id = service.finalResult()?.id;
    recognition.onend?.();
    expect(service.finalResult()?.id).toBe(id);
  });

  it('sale de Escuchando y muestra error controlado', () => {
    service.start();
    recognition.onerror?.({ error: 'not-allowed' });
    expect(service.isListening()).toBe(false);
    expect(service.lastError()).toContain('bloqueó el micrófono');
  });
});
