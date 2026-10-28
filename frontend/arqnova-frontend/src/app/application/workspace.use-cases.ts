import { UmlDiagramModel } from '../domain/uml/uml-class-diagram.models';
import { BackendGenerationConfig, FlutterGenerationConfig } from '../domain/uml/generation.models';
import { UmlSession, UmlLiveEvent } from '../domain/uml/collaboration.models';
import {
  AdminUser,
  CrearAdminUserRequest,
  ActualizarAdminUserRequest,
} from '../domain/auth/admin.models';
import {
  UmlAiPort,
  BackendGenerationPort,
  FlutterGenerationPort,
  XmiPort,
  CollaborationPort,
  VoicePort,
  UserAdminPort,
  FileUpload,
} from './ports/workspace.ports';
export class UmlAiUseCases {
  constructor(private readonly driver: UmlAiPort) {}
  interpret(instruction: string, model: UmlDiagramModel) {
    return this.driver.interpret(instruction, model);
  }
  analyzeImage(image: FileUpload) {
    return this.driver.analyzeImage(image);
  }
}
export class BackendGenerationUseCases {
  constructor(private readonly driver: BackendGenerationPort) {}
  generate(config: BackendGenerationConfig) {
    return this.driver.generate(config);
  }
  generatePostman(config: BackendGenerationConfig) {
    return this.driver.generatePostman(config);
  }
}
export class FlutterGenerationUseCases {
  constructor(private readonly driver: FlutterGenerationPort) {}
  generate(config: FlutterGenerationConfig) {
    return this.driver.generate(config);
  }
}
export class XmiUseCases {
  constructor(private readonly driver: XmiPort) {}
  decode(buffer: ArrayBuffer) {
    return this.driver.decode(buffer);
  }
  parse(xml: string) {
    return this.driver.parse(xml);
  }
  serialize(model: UmlDiagramModel) {
    return this.driver.serialize(model);
  }
}
export class CollaborationUseCases {
  constructor(private readonly driver: CollaborationPort) {}
  start(projectId: string, projectName: string, model: UmlDiagramModel) {
    return this.driver.start(projectId, projectName, model);
  }
  join(code: string) {
    return this.driver.join(code);
  }
  activities(sessionId: string) {
    return this.driver.activities(sessionId);
  }
  connect(session: UmlSession, handler: (event: UmlLiveEvent) => void) {
    this.driver.connect(session, handler);
  }
  publish(event: UmlLiveEvent) {
    this.driver.publish(event);
  }
  disconnect(announce = true) {
    this.driver.disconnect(announce);
  }
}
export class VoiceUseCases {
  constructor(private readonly driver: VoicePort) {}
  get recognition() {
    return this.driver.recognition;
  }
  set recognition(value: any) {
    this.driver.recognition = value;
  }
  get isListening() {
    return this.driver.isListening;
  }
  get transcript() {
    return this.driver.transcript;
  }
  get finalResult() {
    return this.driver.finalResult;
  }
  get isSupported() {
    return this.driver.isSupported;
  }
  get lastError() {
    return this.driver.lastError;
  }
  init() {
    this.driver.init();
  }
  start(autoStopOnFinal = false) {
    return this.driver.start(autoStopOnFinal);
  }
  stop(processTranscript = false) {
    return this.driver.stop(processTranscript);
  }
  clear() {
    this.driver.clear();
  }
  getInterim() {
    return this.driver.getInterim();
  }
  getTranscriptCandidate() {
    return this.driver.getTranscriptCandidate();
  }
}
export class UserAdminUseCases {
  constructor(private readonly driver: UserAdminPort) {}
  listarUsuarios() {
    return this.driver.listarUsuarios();
  }
  crearUsuario(request: CrearAdminUserRequest) {
    return this.driver.crearUsuario(request);
  }
  actualizarUsuario(id: string, request: ActualizarAdminUserRequest) {
    return this.driver.actualizarUsuario(id, request);
  }
  eliminarUsuario(id: string) {
    return this.driver.eliminarUsuario(id);
  }
  subirAvatar(id: string, file: FileUpload) {
    return this.driver.subirAvatar(id, file);
  }
  actualizarEstado(id: string, value: boolean) {
    return this.driver.actualizarEstado(id, value);
  }
  listarDepartamentos() {
    return this.driver.listarDepartamentos();
  }
}
