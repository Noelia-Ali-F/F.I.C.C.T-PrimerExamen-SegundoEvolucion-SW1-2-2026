import { Observable } from 'rxjs';
import { StateCell } from './platform.ports';
import { UmlDiagramModel } from '../../domain/uml/uml-class-diagram.models';
import { UmlAiCommand, UmlImageAnalysis } from '../../domain/uml/ai.models';
import { UmlSession, UmlLiveEvent } from '../../domain/uml/collaboration.models';
import {
  BackendGenerationConfig,
  FlutterGenerationConfig,
} from '../../domain/uml/generation.models';
import { XmiPreview } from '../../domain/uml/xmi.models';
import {
  AdminUser,
  CrearAdminUserRequest,
  ActualizarAdminUserRequest,
} from '../../domain/auth/admin.models';
export interface FileUpload {
  readonly name: string;
  readonly size: number;
  readonly type: string;
  arrayBuffer(): Promise<ArrayBuffer>;
}
export interface FileDownload {
  body: Blob | null;
  headers: { get(name: string): string | null };
}
export interface UmlAiPort {
  interpret(instruction: string, model: UmlDiagramModel): Observable<UmlAiCommand>;
  analyzeImage(image: FileUpload): Observable<UmlImageAnalysis>;
}
export interface BackendGenerationPort {
  generate(config: BackendGenerationConfig): Observable<FileDownload>;
  generatePostman(config: BackendGenerationConfig): Observable<FileDownload>;
}
export interface FlutterGenerationPort {
  generate(config: FlutterGenerationConfig): Observable<FileDownload>;
}
export interface XmiPort {
  decode(buffer: ArrayBuffer): string;
  parse(xml: string): XmiPreview;
  serialize(model: UmlDiagramModel): string;
}
export interface SessionReaderPort {
  currentUser(): { token?: string } | null;
}
export interface CollaborationPort {
  start(projectId: string, projectName: string, model: UmlDiagramModel): Observable<UmlSession>;
  join(inviteCode: string): Observable<UmlSession>;
  activities(sessionId: string): Observable<UmlLiveEvent[]>;
  connect(session: UmlSession, handler: (event: UmlLiveEvent) => void): void;
  publish(event: UmlLiveEvent): void;
  disconnect(announce?: boolean): void;
}
export interface VoicePort {
  recognition: any;
  isListening: StateCell<boolean>;
  transcript: StateCell<string>;
  finalResult: StateCell<{ id: number; text: string } | null>;
  isSupported: StateCell<boolean>;
  lastError: StateCell<string>;
  init(): void;
  start(autoStopOnFinal?: boolean): boolean;
  stop(processTranscript?: boolean): boolean;
  clear(): void;
  getInterim(): string;
  getTranscriptCandidate(): string;
}
export interface UserAdminPort {
  listarUsuarios(): Observable<AdminUser[]>;
  crearUsuario(request: CrearAdminUserRequest): Observable<AdminUser>;
  actualizarUsuario(id: string, request: ActualizarAdminUserRequest): Observable<AdminUser>;
  eliminarUsuario(id: string): Observable<void>;
  subirAvatar(id: string, archivo: FileUpload): Observable<AdminUser>;
  actualizarEstado(id: string, habilitado: boolean): Observable<AdminUser>;
  listarDepartamentos(): Observable<string[]>;
}
