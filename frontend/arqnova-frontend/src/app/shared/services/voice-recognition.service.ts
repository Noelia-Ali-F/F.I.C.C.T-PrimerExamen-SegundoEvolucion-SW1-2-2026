import { Injectable,NgZone } from '@angular/core';
import { VoiceUseCases } from '../../application/workspace.use-cases';
import { BrowserVoiceAdapter } from '../../infrastructure/adapters/browser-voice.adapter';
@Injectable({providedIn:'root'})export class VoiceRecognitionService extends VoiceUseCases {constructor(zone:NgZone){super(new BrowserVoiceAdapter(zone));}}
