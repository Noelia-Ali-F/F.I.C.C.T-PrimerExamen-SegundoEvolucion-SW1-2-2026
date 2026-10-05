import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, ViewChild, computed, effect, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { UmlAttribute, UmlClassNode, UmlDiagramModel, UmlMethod, UmlRelation, UmlRelationType } from './uml-class-diagram.models';
import { UmlProjectStoreService } from './uml-project-store.service';
import { AuthService } from '../../auth/auth.service';
import { UmlCollaborationService, UmlElementLock, UmlLiveEvent, UmlLockElementType, UmlParticipant, UmlSession } from './uml-collaboration.service';
import { UmlAiCommand, UmlAiCommandService, UmlImageAnalysis } from './uml-ai-command.service';
import { VoiceRecognitionService } from '../../shared/services/voice-recognition.service';
import { UmlXmiService, XmiPreview } from './uml-xmi.service';
import { BackendGenerationConfig, UmlBackendGeneratorService } from './uml-backend-generator.service';
import { UmlFlutterGeneratorService } from './uml-flutter-generator.service';

type DialogAction = 'delete-class' | 'delete-relation' | 'reset' | null;
interface AiHistoryItem { instruction: string; message: string; kind: 'success' | 'error' | 'clarification'; at: Date; }

@Component({
  selector: 'app-uml-class-diagram', standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  templateUrl: './uml-class-diagram.component.html',
  styleUrls: ['./uml-class-diagram.component.scss', './uml-backend-generator.scss', './uml-toolbar-tooltips.scss']
})
export class UmlClassDiagramComponent implements OnDestroy {
  private readonly backendGenerator = inject(UmlBackendGeneratorService);
  private readonly flutterGenerator = inject(UmlFlutterGeneratorService);
  private readonly xmi = inject(UmlXmiService);
  xmiPreview = signal<XmiPreview|null>(null);
  xmiLoading = signal(false);
  async importXmi(event:Event) {
    const input=event.target as HTMLInputElement,file=input.files?.[0]; input.value='';
    if(!file)return;
    this.xmiPreview.set(null);
    if(!/\.(xmi|xml)$/i.test(file.name)||file.size>10*1024*1024)return this.showNotice('error','Selecciona un archivo .xmi/.xml de hasta 10 MB.');
    this.xmiLoading.set(true);
    try {
      const preview=this.xmi.parse(this.xmi.decode(await file.arrayBuffer()));
      const collisions=preview.model.classes.filter(c=>this.classes().some(existing=>existing.id===c.id||this.normalizedName(existing.name)===this.normalizedName(c.name)));
      if(collisions.length)preview.warnings.push(`No se sobrescribirán clases existentes: ${collisions.map(c=>c.name).join(', ')}. Se omitirán también sus relaciones importadas.`);
      this.xmiPreview.set(preview);
    } catch(error){this.showNotice('error',error instanceof Error?error.message:'No se pudo leer el archivo XMI.');}
    finally{this.xmiLoading.set(false);}
  }
  confirmXmiImport() {
    const preview=this.xmiPreview();if(!preview)return;
    const classes=preview.model.classes.filter(c=>!this.classes().some(existing=>existing.id===c.id||this.normalizedName(existing.name)===this.normalizedName(c.name)));
    const ids=new Set(classes.map(c=>c.id));
    const relations=preview.model.relations.filter(r=>ids.has(r.sourceId)&&ids.has(r.targetId)&&!this.relations().some(existing=>existing.id===r.id));
    if(!classes.length)return this.showNotice('error','No hay clases nuevas para insertar.');
    this.updateModel({...this.model(),classes:[...this.classes(),...classes],relations:[...this.relations(),...relations]},'Modelo XMI importado');
    classes.forEach(c=>this.broadcast('class:create',c.id,c,'importó una clase XMI'));
    relations.forEach(r=>this.broadcast('relation:create',r.id,r,'importó una relación XMI','relation'));
    this.clearSelection();this.xmiPreview.set(null);
  }
  exportXmi(){try{this.download(`${this.safeTitle()}-EA.xmi`,this.xmi.serialize(this.model()),'application/xml');this.status.set('XMI para Enterprise Architect exportado');}catch(error){this.showNotice('error',error instanceof Error?error.message:'No se pudo exportar XMI.');}}
  @ViewChild('canvas', { static: true }) canvas!: ElementRef<HTMLElement>;
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;
  private readonly projectId: string;
  readonly canvasWidth = 1500;
  readonly canvasHeight = 900;
  readonly cardWidth = 270;
  readonly relationTypes: UmlRelationType[] = ['association', 'aggregation', 'composition', 'inheritance', 'dependency'];
  readonly attributeTypes = ['String', 'Integer', 'Long', 'Boolean', 'Double', 'Float', 'BigDecimal', 'Date', 'DateTime', 'UUID'] as const;

  model = signal<UmlDiagramModel>({ format: 'uml-class-diagram', version: 2, title: '', classes: [], relations: [] });
  selectedClassId = signal<string | null>(null);
  selectedRelationId = signal<string | null>(null);
  zoom = signal(0.82);
  status = signal('Cargando proyecto...');
  lastSavedAt = signal<Date | null>(this.model().updatedAt ? new Date(this.model().updatedAt!) : null);
  notice = signal<{ kind: 'error' | 'success'; text: string } | null>(null);
  dialogAction = signal<DialogAction>(null);
  toolsCollapsed = signal(false);
  inspectorCollapsed = signal(false);
  collaborationModal = signal(false);
  shareModal = signal(false);
  collaborativeSession = signal<UmlSession | null>(null);
  participants = signal<UmlParticipant[]>([]);
  activity = signal<UmlLiveEvent[]>([]);
  remoteActions = signal<Record<string, { userName: string; action: string }>>({});
  remoteCursors = signal<Record<string, { userName: string; x: number; y: number; color: string }>>({});
  elementLocks = signal<Record<string, UmlElementLock>>({});
  pendingLock = signal<string | null>(null);
  collaborationError = signal('');
  aiPanelOpen = signal(false);
  aiMode = signal<'text' | 'voice'>('text');
  aiInstruction = signal('');
  aiBusy = signal(false);
  aiResult = signal<{ kind: 'success' | 'error' | 'clarification'; text: string } | null>(null);
  aiHistory = signal<AiHistoryItem[]>([]);
  imageModalOpen = signal(false);
  imageProcessing = signal(false);
  imageAnalysis = signal<UmlImageAnalysis | null>(null);
  imageError = signal('');
  imagePreviewUrl = signal('');
  imageFile: File | null = null;
  backendModalOpen = signal(false);
  backendGenerating = signal(false);
  backendProgress = signal(0);
  backendError = signal('');
  generatedBackend = signal<{blob: Blob; fileName: string; classes: number; relations: number} | null>(null);
  postmanGenerating = signal(false);
  postmanProgress = signal(0);
  generatedPostman = signal<{blob:Blob;fileName:string;entities:number;requests:number}|null>(null);
  private lastBackendRequest: BackendGenerationConfig|null = null;
  backendConfig = { projectName: '', basePackage: 'com.generated.app', includeBasicConfig: true };
  flutterModalOpen = signal(false);
  flutterGenerating = signal(false);
  flutterProgress = signal(0);
  flutterError = signal('');
  generatedFlutter = signal<{blob: Blob; fileName: string; models: number; screens: number} | null>(null);
  flutterConfig = { projectName: '', backendBaseUrl: 'http://10.0.2.2:8080' };

  newClassName = '';
  attributeDraft: UmlAttribute = this.emptyAttribute();
  editingAttributeId: string | null = null;
  methodDraft: UmlMethod = this.emptyMethod();
  editingMethodId: string | null = null;
  relationDraft = { from: '', to: '', type: 'association' as UmlRelationType, label: '', fromMultiplicity: '1', toMultiplicity: '0..*' };
  associationClassName = '';
  private dragging: { id: string; offsetX: number; offsetY: number } | null = null;
  private dragCandidate: { id:string; startX:number; startY:number; event:PointerEvent } | null = null;
  private lastCursorSentAt = 0;
  private pendingLockActions = new Map<string, () => void>();
  private readonly lockRenewTimer = window.setInterval(() => this.renewOwnedLocks(), 20_000);
  private voiceCaptureActive = false;
  private lastVoiceResultId = 0;
  private saveTimer: number | null = null;
  private saveInFlight = false;
  private pendingSaveMessage: string | null = null;
  private explicitSavePending = false;

  classes = computed(() => this.model().classes);
  relations = computed(() => this.model().relations);
  selectedClass = computed(() => this.classes().find(item => item.id === this.selectedClassId()) ?? null);
  selectedRelation = computed(() => this.relations().find(item => item.id === this.selectedRelationId()) ?? null);
  sessionHost = computed(() => {
    const session = this.collaborativeSession();
    return this.participants().find(participant => participant.host || participant.userId === session?.hostUserId) ?? null;
  });
  sessionHostName = computed(() => this.sessionHost()?.userName || this.collaborativeSession()?.hostUserId || 'Anfitrión');

  constructor(private readonly router: Router, private readonly route: ActivatedRoute, private readonly projectStore: UmlProjectStoreService,
              private readonly collaboration: UmlCollaborationService, private readonly auth: AuthService,
              private readonly umlAi: UmlAiCommandService, public readonly voice: VoiceRecognitionService) {
    this.projectId = this.route.snapshot.paramMap.get('projectId') ?? '';
    const invite = this.route.snapshot.queryParamMap.get('invite');
    effect(() => {
      const result = this.voice.finalResult();
      if (!this.voiceCaptureActive || !result || result.id === this.lastVoiceResultId) return;
      this.lastVoiceResultId = result.id;
      this.voiceCaptureActive = false;
      this.aiInstruction.set(result.text);
      window.setTimeout(() => this.sendAiInstruction(), 0);
    });
    if (invite) { this.joinByInvitation(invite); return; }
    this.projectStore.get(this.projectId).subscribe({
      next: project => {
        this.model.set(this.normalizeModel(project.model));
        this.lastSavedAt.set(new Date(project.updatedAt));
        this.status.set('Proyecto cargado desde PostgreSQL');
        if (this.route.snapshot.queryParamMap.get('collaborate') === '1') this.startCollaboration();
      },
      error: () => {
        const cached=this.projectStore.cached(this.projectId);
        if(cached){this.model.set(this.normalizeModel(cached.model));this.lastSavedAt.set(new Date(cached.updatedAt));this.status.set('Sin conexión: copia local abierta sin sobrescribir PostgreSQL');return;}
        this.router.navigate(['/proyectos']);
      }
    });
  }

  ngOnDestroy() { window.clearInterval(this.lockRenewTimer);if(this.saveTimer!==null)window.clearTimeout(this.saveTimer);this.voiceCaptureActive=false;this.voice.stop(); this.collaboration.disconnect(); this.revokeImagePreview(); }

  openBackendGenerator() {
    const base=this.safeTitle()||'uml-backend';
    this.backendConfig={projectName:base,basePackage:`com.generated.${base.replace(/[^a-zA-Z0-9]/g,'').toLowerCase()||'app'}`,includeBasicConfig:true};
    this.backendError.set('');this.generatedBackend.set(null);this.generatedPostman.set(null);this.backendProgress.set(0);this.postmanProgress.set(0);this.backendModalOpen.set(true);
  }
  closeBackendGenerator(){if(!this.backendGenerating())this.backendModalOpen.set(false);}
  generateBackend(){
    if(this.backendGenerating())return;
    const projectName=this.backendConfig.projectName.trim(),basePackage=this.backendConfig.basePackage.trim();
    if(!projectName){this.backendError.set('El nombre del proyecto es obligatorio.');return;}
    if(basePackage&&!/^[a-zA-Z_$][\w$]*(\.[a-zA-Z_$][\w$]*)*$/.test(basePackage)){this.backendError.set('El paquete base no es válido. Ejemplo: com.empresa.proyecto');return;}
    if(!this.classes().length){this.backendError.set('El diagrama debe contener al menos una clase.');return;}
    const invalid=this.classes().find(c=>!c.name.trim()||c.attributes.some(a=>!a.name.trim()||!a.type.trim()));
    if(invalid){this.backendError.set(`Completa los nombres y tipos de la clase ${invalid.name||'sin nombre'}.`);return;}
    this.backendGenerating.set(true);this.backendProgress.set(18);this.backendError.set('');this.generatedBackend.set(null);
    const timer=window.setInterval(()=>this.backendProgress.update(value=>Math.min(value+14,88)),350);
    const request={projectName,basePackage,includeBasicConfig:this.backendConfig.includeBasicConfig,model:this.model()};this.lastBackendRequest=request;
    this.backendGenerator.generate(request).subscribe({
      next:response=>{window.clearInterval(timer);this.backendGenerating.set(false);this.backendProgress.set(100);const disposition=response.headers.get('content-disposition')||'';const fileName=/filename="?([^";]+)"?/i.exec(disposition)?.[1]||`${projectName}.zip`;this.generatedBackend.set({blob:response.body!,fileName,classes:Number(response.headers.get('x-generated-classes')||this.classes().length),relations:Number(response.headers.get('x-generated-relations')||this.relations().length)});},
      error:error=>{window.clearInterval(timer);this.backendGenerating.set(false);this.backendProgress.set(0);this.showBackendGenerationError(error);}
    });
  }
  private async showBackendGenerationError(error:any){
    let message='';
    try{
      const raw=error?.error instanceof Blob?await error.error.text():error?.error;
      const body=typeof raw==='string'?JSON.parse(raw):raw;
      message=body?.mensaje||body?.message||'';
    }catch{ /* respuesta sin JSON controlado */ }
    this.backendError.set(message||(error?.status===400?'El modelo contiene información incompleta o no soportada. Revisa nombres, tipos y relaciones.':'No se pudo generar el backend. Verifica que el servicio esté disponible.'));
  }
  downloadGeneratedBackend(){const generated=this.generatedBackend();if(!generated)return;const url=URL.createObjectURL(generated.blob),anchor=document.createElement('a');anchor.href=url;anchor.download=generated.fileName;anchor.click();URL.revokeObjectURL(url);}
  generatePostman(){const request=this.lastBackendRequest;if(!request||this.postmanGenerating())return;this.postmanGenerating.set(true);this.postmanProgress.set(20);this.backendError.set('');const timer=window.setInterval(()=>this.postmanProgress.update(value=>Math.min(value+18,88)),250);this.backendGenerator.generatePostman(request).subscribe({next:response=>{window.clearInterval(timer);this.postmanGenerating.set(false);this.postmanProgress.set(100);this.generatedPostman.set({blob:response.body!,fileName:'collection.json',entities:Number(response.headers.get('x-postman-entities')||this.classes().length),requests:Number(response.headers.get('x-postman-requests')||this.classes().length*5)});},error:error=>{window.clearInterval(timer);this.postmanGenerating.set(false);this.postmanProgress.set(0);this.showBackendGenerationError(error);}});}
  downloadPostman(){const generated=this.generatedPostman();if(!generated)return;const url=URL.createObjectURL(generated.blob),anchor=document.createElement('a');anchor.href=url;anchor.download=generated.fileName;anchor.click();URL.revokeObjectURL(url);}

  openFlutterGenerator(){
    const base=(this.safeTitle()||'uml').replace(/[^a-zA-Z0-9]+/g,'_').replace(/^_|_$/g,'').toLowerCase()||'uml';
    this.flutterConfig={projectName:`${base}_mobile`,backendBaseUrl:'http://10.0.2.2:8080'};
    this.flutterError.set('');this.generatedFlutter.set(null);this.flutterProgress.set(0);this.flutterModalOpen.set(true);
  }
  closeFlutterGenerator(){if(!this.flutterGenerating())this.flutterModalOpen.set(false);}
  generateFlutter(){
    if(this.flutterGenerating())return;
    const projectName=this.flutterConfig.projectName.trim(),backendBaseUrl=this.flutterConfig.backendBaseUrl.trim();
    if(!projectName){this.flutterError.set('El nombre del proyecto Flutter es obligatorio.');return;}
    if(!/^https?:\/\/\S+$/.test(backendBaseUrl)){this.flutterError.set('La URL del backend debe comenzar con http:// o https://.');return;}
    if(!this.classes().length){this.flutterError.set('El diagrama debe contener al menos una clase.');return;}
    const invalid=this.classes().find(c=>!c.name.trim()||c.attributes.some(a=>!a.name.trim()||!a.type.trim()));
    if(invalid){this.flutterError.set(`Completa los nombres y tipos de la clase ${invalid.name||'sin nombre'}.`);return;}
    this.flutterGenerating.set(true);this.flutterProgress.set(15);this.flutterError.set('');this.generatedFlutter.set(null);
    const timer=window.setInterval(()=>this.flutterProgress.update(value=>Math.min(value+15,90)),300);
    this.flutterGenerator.generate({projectName,backendBaseUrl,model:this.model()}).subscribe({
      next:response=>{window.clearInterval(timer);this.flutterGenerating.set(false);this.flutterProgress.set(100);const disposition=response.headers.get('content-disposition')||'';const fileName=/filename="?([^";]+)"?/i.exec(disposition)?.[1]||`${projectName}-flutter.zip`;this.generatedFlutter.set({blob:response.body!,fileName,models:Number(response.headers.get('x-generated-models')||this.classes().length),screens:Number(response.headers.get('x-generated-screens')||this.classes().length*2+1)});},
      error:error=>{window.clearInterval(timer);this.flutterGenerating.set(false);this.flutterProgress.set(0);this.showFlutterGenerationError(error);}
    });
  }
  private async showFlutterGenerationError(error:any){
    let message='';try{const raw=error?.error instanceof Blob?await error.error.text():error?.error;const body=typeof raw==='string'?JSON.parse(raw):raw;message=body?.mensaje||body?.message||'';}catch{/* respuesta no JSON */}
    this.flutterError.set(message||(error?.status===400?'El modelo contiene información incompleta o tipos no soportados.':'No se pudo generar el frontend Flutter. Verifica que el servicio esté disponible.'));
  }
  downloadGeneratedFlutter(){const generated=this.generatedFlutter();if(!generated)return;const url=URL.createObjectURL(generated.blob),anchor=document.createElement('a');anchor.href=url;anchor.download=generated.fileName;anchor.click();URL.revokeObjectURL(url);}

  openImageGenerator(){this.resetImageGenerator();this.imageModalOpen.set(true);}
  closeImageGenerator(){if(this.imageProcessing())return;this.imageModalOpen.set(false);this.resetImageGenerator();}
  selectUmlImage(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;this.imageError.set('');if(!['image/png','image/jpeg','image/webp'].includes(file.type)){this.imageError.set('Usa una imagen PNG, JPG, JPEG o WEBP.');return;}if(file.size>10*1024*1024){this.imageError.set('La imagen no puede superar 10 MB.');return;}this.revokeImagePreview();this.imageFile=file;this.imagePreviewUrl.set(URL.createObjectURL(file));this.imageAnalysis.set(null);}
  processUmlImage(){if(!this.imageFile||this.imageProcessing())return;this.imageProcessing.set(true);this.imageAnalysis.set(null);this.imageError.set('');this.umlAi.analyzeImage(this.imageFile).subscribe({next:result=>{this.imageProcessing.set(false);if(result.status!=='READY'){this.imageError.set(result.message);return;}this.imageAnalysis.set({...result,warnings:[...result.warnings,...this.imageConflictWarnings(result)]});},error:error=>{this.imageProcessing.set(false);const messages:Record<string,string>={AI_PROVIDER_UNAVAILABLE:'El servicio de análisis con IA no está disponible temporalmente. Tu imagen no fue modificada. Inténtalo nuevamente más tarde.',AI_RATE_LIMITED:'El servicio de IA alcanzó su límite temporal de solicitudes. Inténtalo nuevamente más tarde.',NO_UML_DETECTED:'No se pudieron identificar elementos UML claramente. Usa una imagen con mejor resolución, contraste y encuadre.',INVALID_IMAGE:'El archivo seleccionado no es una imagen válida.',AI_INVALID_RESPONSE:'El servicio de IA devolvió una respuesta inválida. Inténtalo nuevamente.'};this.imageError.set(messages[error?.error?.code]||(error?.status===429?messages['AI_RATE_LIMITED']:error?.status===503||error?.status===504||error?.status===0?messages['AI_PROVIDER_UNAVAILABLE']:'No se pudo completar el análisis. Inténtalo nuevamente.'));}});}
  generateImageResult(){const analysis=this.imageAnalysis();if(!analysis)return;const existingByName=new Map(this.classes().map(item=>[this.normalizedName(item.name),item]));const inserted:UmlClassNode[]=[];for(const detected of analysis.classes){const key=this.normalizedName(detected.name);if(existingByName.has(key))continue;const count=this.classes().length+inserted.length;const item:UmlClassNode={id:this.uniqueIdAgainst(detected.name,[...this.classes(),...inserted]),name:detected.name,description:'Reconocida desde imagen',stereotype:'Entidad',attributes:detected.attributes.map(value=>({id:this.entityId('attr'),name:value.name,type:value.type,isPrimaryKey:value.isPrimaryKey===true})),methods:(detected.methods||[]).map(method=>({id:this.entityId('method'),name:method.name,parameters:method.parameters||'',returnType:method.returnType||''})),responsibilities:[],x:70+(count%4)*350,y:70+Math.floor(count/4)*300,color:['#1e3a8a','#2563eb','#7c3aed','#b45309'][count%4]};if(Number.isFinite(detected.x)&&Number.isFinite(detected.y)){item.x=Math.round(40+Math.max(0,Math.min(1,detected.x!))*(this.canvasWidth-this.cardWidth-80));item.y=Math.round(40+Math.max(0,Math.min(1,detected.y!))*Math.max(0,this.canvasHeight-this.classHeight(item)-80));}inserted.push(item);existingByName.set(key,item);}
    const insertedRelations:UmlRelation[]=[];for(const detected of analysis.relations){const source=existingByName.get(this.normalizedName(detected.sourceClassName)),target=existingByName.get(this.normalizedName(detected.targetClassName));if(!source||!target)continue;const duplicate=[...this.relations(),...insertedRelations].some(value=>value.sourceId===source.id&&value.targetId===target.id&&value.type===detected.type);if(duplicate)continue;const relation:UmlRelation={id:this.entityId('rel'),sourceId:source.id,targetId:target.id,type:detected.type as UmlRelationType,label:detected.label||'',sourceMultiplicity:detected.sourceMultiplicity,targetMultiplicity:detected.targetMultiplicity};const recognized=this.findRecognizedAssociationClass(detected,analysis,inserted,insertedRelations);if(recognized&&this.needsAssociationClass(relation)){relation.associationClassId=recognized.id;recognized.stereotype='Clase de asociación';recognized.x=Math.max(20,Math.min(this.canvasWidth-this.cardWidth-20,(source.x+target.x)/2));recognized.y=Math.max(20,Math.min(this.canvasHeight-150,(source.y+target.y)/2+130));}else{const associationClass=this.createAssociationClass(relation,[...this.classes(),...inserted],detected.associationClassName);if(associationClass)inserted.push(associationClass);}insertedRelations.push(relation);}
    if(!inserted.length&&!insertedRelations.length){this.imageError.set('No hay elementos nuevos para insertar. Revisa los conflictos indicados.');return;}const next={...this.model(),classes:[...this.classes(),...inserted],relations:[...this.relations(),...insertedRelations]};this.updateModel(next,'Diagrama generado desde imagen');inserted.forEach(item=>this.broadcast('class:create',item.id,item,'creó una clase desde imagen'));insertedRelations.forEach(item=>this.broadcast('relation:create',item.id,item,'creó una relación desde imagen','relation'));this.imageModalOpen.set(false);this.resetImageGenerator();this.showNotice('success',`Se insertaron ${inserted.length} clases y ${insertedRelations.length} relaciones editables.`);}
  private imageConflictWarnings(result:UmlImageAnalysis){const existing=new Set(this.classes().map(item=>this.normalizedName(item.name)));return result.classes.filter(item=>existing.has(this.normalizedName(item.name))).map(item=>`La clase ${item.name} ya existe y no se sobrescribirá.`);}
  private resetImageGenerator(){this.revokeImagePreview();this.imageFile=null;this.imageAnalysis.set(null);this.imageError.set('');this.imageProcessing.set(false);}
  private revokeImagePreview(){const url=this.imagePreviewUrl();if(url)URL.revokeObjectURL(url);this.imagePreviewUrl.set('');}
  private normalizedName(value:string){return value.trim().normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();}
  private uniqueIdAgainst(name:string,items:UmlClassNode[]){const base=name.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')||'clase';let id=base,n=2;while(items.some(i=>i.id===id))id=`${base}-${n++}`;return id;}

  toggleAiPanel() { this.aiPanelOpen.update(value => !value); }
  setAiMode(mode:'text'|'voice') { this.aiMode.set(mode); this.aiResult.set(null); if(mode==='text'&&this.voice.isListening()){this.voiceCaptureActive=false;this.voice.stop(false);} }
  toggleAiVoice() {
    if (this.voice.isListening()) {
      if (!this.voice.stop(true)) {
        this.voiceCaptureActive=false;
        this.finishAi('',this.voice.lastError()||'No se detectó una instrucción de voz.','error');
      }
    } else {
      this.voice.clear();
      this.voiceCaptureActive=this.voice.start(false);
    }
  }
  useVoiceTranscript() { const value=this.voice.getTranscriptCandidate();if(value)this.aiInstruction.set(value); }
  sendAiInstruction() {
    if(this.voice.isListening()){this.voice.stop(true);return;}
    const instruction=this.aiInstruction().trim()||this.voice.getTranscriptCandidate();
    if(!instruction||this.aiBusy())return;
    this.aiInstruction.set(instruction);this.aiBusy.set(true);this.aiResult.set(null);
    this.umlAi.interpret(instruction,this.model()).subscribe({
      next:command=>{this.aiBusy.set(false);const operations=command.operations?.length?command.operations.map(item=>({...command,operation:item.type,targetId:item.targetId,parameters:item.parameters,operations:undefined})):command.operation?[command]:[];if(command.status!=='READY'||!operations.length){const kind=command.status==='CLARIFICATION'?'clarification':'error';this.finishAi(instruction,command.clarificationQuestion||command.message,kind);return;}this.executeAiCommands(operations,0,()=>{this.finishAi(instruction,command.message,'success');this.aiInstruction.set('');this.voice.clear();});},
      error:error=>{this.aiBusy.set(false);const message=error?.error?.mensaje||'No se pudo procesar la instrucción. Verifica que el backend esté disponible.';this.finishAi(instruction,message,'error');}
    });
  }
  clearAiHistory(){this.aiHistory.set([]);this.aiResult.set(null);}

  selectClass(item: UmlClassNode) {
    const previousClass=this.selectedClassId(),previousRelation=this.selectedRelationId();
    if(previousClass&&previousClass!==item.id)this.releaseLock('class',previousClass);
    if(previousRelation)this.releaseLock('relation',previousRelation);
    this.selectedClassId.set(item.id); this.selectedRelationId.set(null);
    this.cancelAttributeEdit(); this.cancelMethodEdit();
    if (!this.relationDraft.from) this.relationDraft.from = item.id;
  }
  selectRelation(relation: UmlRelation, event: Event) { event.stopPropagation(); const previousClass=this.selectedClassId(),previousRelation=this.selectedRelationId();if(previousClass)this.releaseLock('class',previousClass);if(previousRelation&&previousRelation!==relation.id)this.releaseLock('relation',previousRelation);this.selectedRelationId.set(relation.id); this.selectedClassId.set(null); }
  clearSelection() { this.releaseOwnedLocks(); this.selectedClassId.set(null); this.selectedRelationId.set(null); }

  addClass() {
    const name = this.newClassName.trim();
    if (!name) return this.showNotice('error', 'Escribe un nombre para la clase.');
    if (this.classes().some(item => item.name.toLocaleLowerCase() === name.toLocaleLowerCase())) return this.showNotice('error', 'Ya existe una clase con ese nombre.');
    const count = this.classes().length;
    const item: UmlClassNode = { id: this.uniqueId(name), name, description: '', stereotype: 'Entidad', attributes: [], methods: [], responsibilities: [], x: 70 + (count % 4) * 350, y: 70 + Math.floor(count / 4) * 300, color: ['#1e3a8a', '#2563eb', '#7c3aed', '#b45309'][count % 4] };
    this.updateModel({ ...this.model(), classes: [...this.classes(), item] }, 'Clase creada', 'class:create', item.id, item);
    this.newClassName = ''; this.selectClass(item);
  }

  updateSelectedClass(changes: Partial<UmlClassNode>) {
    const id = this.selectedClassId(); if (!id) return;
    if (!this.canEdit('class', id)) return this.lockBlocked('class', id);
    if (changes.name !== undefined && !changes.name.trim()) return this.showNotice('error', 'El nombre de la clase es obligatorio.');
    if (changes.name && this.classes().some(item => item.id !== id && item.name.toLocaleLowerCase() === changes.name!.trim().toLocaleLowerCase())) return this.showNotice('error', 'Ya existe una clase con ese nombre.');
    const normalized = changes.name ? { ...changes, name: changes.name.trim() } : changes;
    const updated = { ...this.classes().find(item => item.id === id)!, ...normalized };
    this.updateModel({ ...this.model(), classes: this.classes().map(item => item.id === id ? updated : item) }, 'Clase actualizada', 'class:update', id, updated, 'class');
  }

  saveAttribute() {
    const selected = this.selectedClass();
    const name = this.attributeDraft.name.trim(), type = this.attributeDraft.type.trim();
    const existingUnknown = selected?.attributes.some(item => item.id === this.editingAttributeId && item.type === '');
    if (!selected || !name || (!type && !existingUnknown)) return this.showNotice('error', 'Nombre y tipo del atributo son obligatorios.');
    if (!this.isSupportedAttributeType(type)) return this.showNotice('error', 'Tipo de atributo no soportado. Seleccione un tipo UML compatible.');
    const isPrimaryKey = !!this.attributeDraft.isPrimaryKey;
    const creating = !this.editingAttributeId;
    const attribute: UmlAttribute = { id: this.editingAttributeId ?? (this.attributeDraft.id || this.entityId('attr')), name, type, isPrimaryKey };
    this.attributeDraft.id = attribute.id;
    if (!this.canEdit('attribute', attribute.id)) {
      if (this.collaborativeSession()) return this.requestLock('attribute', attribute.id, () => this.saveAttribute());
      return this.lockBlocked('attribute', attribute.id);
    }
    const attributes = creating ? [...selected.attributes, attribute] : selected.attributes.map(item => item.id === attribute.id ? attribute : item);
    this.updateModel({ ...this.model(), classes: this.classes().map(item => item.id === selected.id ? { ...item, attributes } : item) }, creating ? 'Atributo creado' : 'Atributo actualizado');
    this.broadcastAttribute(creating ? 'attribute:create' : 'attribute:update', selected.id, attribute.id, attribute);
    this.releaseLock('attribute', attribute.id);
    this.editingAttributeId = null;
    this.attributeDraft = this.emptyAttribute();
  }
  beginAttributeCreate() { if (this.editingAttributeId) return; if (!this.attributeDraft.id) this.attributeDraft.id=this.entityId('attr'); this.requestLock('attribute',this.attributeDraft.id); }
  editAttribute(item: UmlAttribute) { this.requestLock('attribute', item.id, () => { this.editingAttributeId = item.id; this.attributeDraft = { ...item }; }); }
  deleteAttribute(id: string) { this.requestLock('attribute', id, () => { const item = this.selectedClass(); if (!item) return; const attributes=item.attributes.filter(attribute=>attribute.id!==id); this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===item.id?{...value,attributes}:value)},'Atributo eliminado'); this.broadcastAttribute('attribute:delete',item.id,id); this.releaseLock('attribute',id); }); }
  cancelAttributeEdit() { const lockId=this.editingAttributeId??this.attributeDraft.id;if(lockId)this.releaseLock('attribute',lockId); this.editingAttributeId = null; this.attributeDraft = this.emptyAttribute(); }
  isSupportedAttributeType(type:string|undefined|null){return !!type&&this.attributeTypes.includes(type.trim() as typeof this.attributeTypes[number]);}

  saveMethod() {
    const selected = this.selectedClass();
    const name = this.methodDraft.name.trim();
    if (!selected || !name) return this.showNotice('error', 'El nombre del método es obligatorio.');
    if (!this.canEdit('class', selected.id)) {
      if (this.collaborativeSession()) return this.requestLock('class', selected.id, () => this.saveMethod());
      return this.lockBlocked('class', selected.id);
    }
    const value = { id: this.editingMethodId ?? this.entityId('method'), name, parameters: this.methodDraft.parameters.trim(), returnType: this.methodDraft.returnType.trim() || 'void' };
    const methods = this.editingMethodId ? selected.methods.map(item => item.id === this.editingMethodId ? value : item) : [...selected.methods, value];
    this.updateSelectedClass({ methods }); this.cancelMethodEdit();
  }
  editMethod(item: UmlMethod) { const selected=this.selectedClass();if(!selected)return;this.requestLock('class',selected.id,()=>{this.editingMethodId = item.id; this.methodDraft = { ...item };}); }
  deleteMethod(id: string) { const item = this.selectedClass(); if (!item) return;this.requestLock('class',item.id,()=>this.updateSelectedClass({ methods: item.methods.filter(method => method.id !== id) })); }
  cancelMethodEdit() { this.editingMethodId = null; this.methodDraft = this.emptyMethod(); }

  addRelation() {
    const { from, to, type } = this.relationDraft;
    if (!from || !to) return this.showNotice('error', 'Selecciona el origen y el destino.');
    if (from === to) return this.showNotice('error', 'El origen y el destino deben ser diferentes.');
    if (!this.classes().some(item => item.id === from) || !this.classes().some(item => item.id === to) || !this.relationTypes.includes(type)) return this.showNotice('error', 'La relación contiene valores no válidos.');
    if (!this.validMultiplicity(this.relationDraft.fromMultiplicity) || !this.validMultiplicity(this.relationDraft.toMultiplicity)) return this.showNotice('error', 'Usa multiplicidades como 1, 0..1, 1..* o 0..*.');
    const relation: UmlRelation = { id: this.entityId('rel'), sourceId: from, targetId: to, type, label: this.relationDraft.label.trim(), sourceMultiplicity: this.relationDraft.fromMultiplicity.trim(), targetMultiplicity: this.relationDraft.toMultiplicity.trim() };
    const associationClass = this.createAssociationClass(relation, this.classes(), this.associationClassName);
    this.updateModel({ ...this.model(), classes: associationClass ? [...this.classes(), associationClass] : this.classes(), relations: [...this.relations(), relation] }, 'Relación creada');
    if (associationClass) this.broadcast('class:create', associationClass.id, associationClass, 'creó una clase de asociación');
    this.broadcast('relation:create', relation.id, relation, 'creó una relación', 'relation');
    this.relationDraft.label = ''; this.associationClassName = ''; this.selectedRelationId.set(relation.id); this.selectedClassId.set(null);
  }
  updateSelectedRelation(changes: Partial<UmlRelation>) {
    const id = this.selectedRelationId(); if (!id) return;
    if (!this.canEdit('relation', id)) return this.lockBlocked('relation', id);
    if ((changes.sourceMultiplicity !== undefined && !this.validMultiplicity(changes.sourceMultiplicity)) || (changes.targetMultiplicity !== undefined && !this.validMultiplicity(changes.targetMultiplicity))) return this.showNotice('error', 'Usa multiplicidades como 1, 0..1, 1..* o 0..*.');
    const updated = { ...this.relations().find(item => item.id === id)!, ...changes };
    if (!this.classes().some(item => item.id === updated.sourceId) || !this.classes().some(item => item.id === updated.targetId)) return this.showNotice('error', 'La relación debe conectar clases existentes.');
    if (updated.sourceId === updated.targetId) return this.showNotice('error', 'El origen y el destino deben ser diferentes.');
    const associationClass = this.createAssociationClass(updated, this.classes());
    this.updateModel({ ...this.model(), classes: associationClass ? [...this.classes(), associationClass] : this.classes(), relations: this.relations().map(item => item.id === id ? updated : item) }, 'Relación actualizada');
    if (associationClass) this.broadcast('class:create', associationClass.id, associationClass, 'creó una clase de asociación');
    this.broadcast('relation:update', id, updated, 'actualizó una relación', 'relation');
  }

  requestDeleteClass() { if (this.selectedClass()) this.dialogAction.set('delete-class'); }
  requestDeleteRelation() { if (this.selectedRelation()) this.dialogAction.set('delete-relation'); }
  requestReset() { this.dialogAction.set('reset'); }
  cancelDialog() { this.dialogAction.set(null); }
  confirmDialog() {
    const action = this.dialogAction(); this.dialogAction.set(null);
    if (action === 'delete-class') this.deleteSelectedClass();
    if (action === 'delete-relation') this.deleteSelectedRelation();
    if (action === 'reset') { this.updateModel({ ...this.model(), classes: [], relations: [] }, 'Diagrama restablecido en blanco'); this.clearSelection(); }
  }
  private deleteSelectedClass() { const id = this.selectedClassId(); if (!id) return; if(!this.canEdit('class',id))return this.lockBlocked('class',id); const linkedIds=this.relations().filter(rel=>rel.sourceId===id||rel.targetId===id).map(rel=>rel.associationClassId).filter((value):value is string=>!!value); this.updateModel({ ...this.model(), classes: this.classes().filter(item => item.id !== id && !linkedIds.includes(item.id)), relations: this.relations().filter(rel => rel.sourceId !== id && rel.targetId !== id).map(rel => rel.associationClassId === id ? {...rel,associationClassId:undefined} : rel) }, 'Clase y relaciones asociadas eliminadas', 'class:delete', id, undefined, 'class'); this.releaseLock('class',id); this.clearSelection(); }
  private deleteSelectedRelation() { const id = this.selectedRelationId(); if (!id) return; if(!this.canEdit('relation',id))return this.lockBlocked('relation',id); const associationClassId=this.selectedRelation()?.associationClassId; this.updateModel({ ...this.model(), classes:associationClassId?this.classes().filter(item=>item.id!==associationClassId):this.classes(), relations: this.relations().filter(rel => rel.id !== id) }, 'Relación eliminada'); if(associationClassId)this.broadcast('class:delete',associationClassId,undefined,'eliminó una clase de asociación','class'); this.broadcast('relation:delete',id,undefined,'eliminó una relación','relation'); this.releaseLock('relation',id); this.clearSelection(); }

  startDrag(event: PointerEvent, item: UmlClassNode) { if (event.button !== 0) return; this.selectClass(item);this.dragCandidate={id:item.id,startX:event.clientX,startY:event.clientY,event};if(this.canEdit('class',item.id))this.beginDragging(event,item); }
  drag(event: PointerEvent) { this.shareCursor(event);if(this.dragCandidate&&!this.dragging&&Math.hypot(event.clientX-this.dragCandidate.startX,event.clientY-this.dragCandidate.startY)>4){const candidate=this.dragCandidate,item=this.classes().find(value=>value.id===candidate.id);this.dragCandidate=null;if(item)this.requestLock('class',item.id,()=>this.beginDragging(candidate.event,item));} if (!this.dragging) return; const rect = this.canvas.nativeElement.getBoundingClientRect(); const x = Math.max(10, Math.min(this.canvasWidth - this.cardWidth - 10, (event.clientX - rect.left) / this.zoom() - this.dragging.offsetX)); const y = Math.max(10, Math.min(this.canvasHeight - 220, (event.clientY - rect.top) / this.zoom() - this.dragging.offsetY)); this.model.set({ ...this.model(), classes: this.classes().map(item => item.id === this.dragging?.id ? { ...item, x: Math.round(x), y: Math.round(y) } : item) }); }
  endDrag() { this.dragCandidate=null;if (this.dragging) { const id=this.dragging.id; const item=this.classes().find(value=>value.id===id); this.dragging = null; this.persist('Posición guardada'); if(item)this.broadcast('class:move',id,item,`movió ${item.name}`,'class'); } }
  leaveCanvas() { this.endDrag(); this.broadcastCursor(0, 0, false); }

  relationGeometry(relation: UmlRelation) { const source = this.classes().find(i => i.id === relation.sourceId), target = this.classes().find(i => i.id === relation.targetId); if (!source || !target) return { x1: 0, y1: 0, x2: 0, y2: 0, midX: 0, midY: 0, fromLabelX: 0, fromLabelY: 0, toLabelX: 0, toLabelY: 0 }; const sc = { x: source.x + this.cardWidth / 2, y: source.y + this.classHeight(source) / 2 }, tc = { x: target.x + this.cardWidth / 2, y: target.y + this.classHeight(target) / 2 }; const a = this.rectangleEdge(sc, tc, this.cardWidth, this.classHeight(source)), b = this.rectangleEdge(tc, sc, this.cardWidth, this.classHeight(target)); const distance = Math.hypot(b.x - a.x, b.y - a.y) || 1, ux = (b.x - a.x) / distance, uy = (b.y - a.y) / distance; return { x1: a.x, y1: a.y, x2: b.x, y2: b.y, midX: (a.x + b.x) / 2, midY: (a.y + b.y) / 2, fromLabelX: a.x + ux * 17 - uy * 13, fromLabelY: a.y + uy * 17 + ux * 13, toLabelX: b.x - ux * 17 - uy * 13, toLabelY: b.y - uy * 17 + ux * 13 }; }
  private rectangleEdge(center: {x:number;y:number}, toward: {x:number;y:number}, width:number, height:number) { const dx=toward.x-center.x, dy=toward.y-center.y; if (!dx && !dy) return center; const scale=Math.min(dx ? width/2/Math.abs(dx) : Infinity, dy ? height/2/Math.abs(dy) : Infinity); return {x:center.x+dx*scale,y:center.y+dy*scale}; }
  classHeight(item: UmlClassNode) { return 74 + Math.max(1,item.attributes.length)*18 + Math.max(1,item.methods.length)*18; }
  attributeText(item: UmlAttribute) { return `${item.isPrimaryKey ? 'PK ' : ''}${item.name}: ${item.type || 'Sin definir'}`; }
  methodText(item: UmlMethod) { return `${item.name}(${item.parameters}): ${item.returnType || 'void'}`; }
  relationTypeLabel(type: UmlRelationType) { return ({ association:'Asociación', aggregation:'Agregación', composition:'Composición', inheritance:'Herencia', dependency:'Dependencia' })[type]; }
  draftNeedsAssociationClass() { return this.relationDraft.type === 'association' && this.relationDraft.fromMultiplicity.trim().endsWith('*') && this.relationDraft.toMultiplicity.trim().endsWith('*'); }
  associationClassFor(relation: UmlRelation) { return this.classes().find(item => item.id === relation.associationClassId) ?? null; }
  private findRecognizedAssociationClass(detected: UmlImageAnalysis['relations'][number], analysis: UmlImageAnalysis, inserted: UmlClassNode[], relations: UmlRelation[]): UmlClassNode | null {
    const used = new Set(relations.map(item => item.associationClassId));
    const available = inserted.filter(item => !used.has(item.id) && !analysis.relations.some(relation =>
      this.normalizedName(relation.sourceClassName) === this.normalizedName(item.name) || this.normalizedName(relation.targetClassName) === this.normalizedName(item.name)));
    const explicit = detected.associationClassName?.trim();
    if (explicit) return available.find(item => this.normalizedName(item.name) === this.normalizedName(explicit)) ?? null;
    const manyToMany = analysis.relations.filter(relation => relation.type === 'association' && relation.sourceMultiplicity.trim().endsWith('*') && relation.targetMultiplicity.trim().endsWith('*'));
    if (manyToMany.length !== 1) return null;
    const detailCandidates = available.filter(item => /^detalle(?:$|[\s_-]|[A-Z])/iu.test(item.name));
    return detailCandidates.length === 1 ? detailCandidates[0] : null;
  }
  private needsAssociationClass(relation: UmlRelation) { return relation.type === 'association' && relation.sourceMultiplicity.trim().endsWith('*') && relation.targetMultiplicity.trim().endsWith('*'); }
  private createAssociationClass(relation: UmlRelation, classes: UmlClassNode[], requestedName = ''): UmlClassNode | null {
    if (!this.needsAssociationClass(relation) || (relation.associationClassId && classes.some(item => item.id === relation.associationClassId))) return null;
    const source = classes.find(item => item.id === relation.sourceId), target = classes.find(item => item.id === relation.targetId);
    if (!source || !target) return null;
    const base = requestedName.trim() || `Detalle${source.name}${target.name}`;
    let name = base, suffix = 2;
    while (classes.some(item => this.normalizedName(item.name) === this.normalizedName(name))) name = `${base}${suffix++}`;
    const centerX = (source.x + target.x + this.cardWidth) / 2;
    const centerY = (source.y + target.y + this.classHeight(source) / 2 + this.classHeight(target) / 2) / 2;
    const associationClass: UmlClassNode = { id: this.entityId('assoc'), name, description: 'Atributos de la asociación', stereotype: 'Clase de asociación',
      attributes: [], methods: [], responsibilities: [], x: Math.max(20, Math.min(this.canvasWidth - this.cardWidth - 20, centerX - this.cardWidth / 2)),
      y: Math.max(20, Math.min(this.canvasHeight - 150, centerY + 110)), color: '#2563eb' };
    relation.associationClassId = associationClass.id;
    return associationClass;
  }
  startMarker(relation: UmlRelation) { return relation.type === 'composition' ? 'url(#diamond-composition)' : relation.type === 'aggregation' ? 'url(#diamond-aggregation)' : null; }
  endMarker(relation: UmlRelation) { return relation.type === 'inheritance' ? 'url(#arrow-inheritance)' : null; }
  className(id: string) { return this.classes().find(item => item.id === id)?.name ?? id; }
  changeZoom(delta:number) { this.setZoom(this.zoom()+delta); } setZoom(value:number) { this.zoom.set(Math.max(.5,Math.min(1.3,Math.round(value*10)/10))); }

  saveProject() {
    this.explicitSavePending = true;
    this.persist('Guardado correctamente');
    if (this.saveTimer !== null) window.clearTimeout(this.saveTimer);
    this.saveTimer = null;
    this.flushPersist();
  }

  startCollaboration() {
    this.collaborationError.set('');
    this.collaboration.start(this.projectId, this.model().title, this.model()).subscribe({
      next: session => { this.shareModal.set(false); this.activateSession(session); this.collaborationModal.set(true); },
      error: () => this.collaborationError.set('No se pudo iniciar la sesión colaborativa.')
    });
  }
  openShare() {
    if (this.collaborativeSession()) this.collaborationModal.set(true);
    else this.shareModal.set(true);
  }
  async copyInvitation() {
    const link = this.invitationLink();
    if (!link) return this.showNotice('error', 'No existe un enlace de invitación para copiar.');
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard API no disponible');
      await navigator.clipboard.writeText(link);
      this.showNotice('success', 'Enlace de invitación copiado.');
    } catch {
      const textarea = document.createElement('textarea');
      textarea.value = link;
      textarea.setAttribute('readonly', '');
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      let copied = false;
      try { copied = document.execCommand('copy'); } catch { copied = false; }
      textarea.remove();
      this.showNotice(copied ? 'success' : 'error', copied
        ? 'Enlace de invitación copiado.'
        : 'No se pudo copiar automáticamente. Selecciona el enlace y cópialo manualmente.');
    }
  }
  invitationLink() { const session=this.collaborativeSession(); return session ? `${window.location.origin}/diagrama-clases/${session.projectId}?invite=${session.inviteCode}` : ''; }
  leaveCollaboration() { this.releaseOwnedLocks(); this.collaboration.disconnect(); this.collaborativeSession.set(null); this.participants.set([]); this.remoteActions.set({}); this.remoteCursors.set({}); this.elementLocks.set({}); }
  remoteAction(elementId:string){return this.remoteActions()[elementId];}
  lockFor(elementType:UmlLockElementType,elementId:string){return this.elementLocks()[this.lockKey(elementType,elementId)];}
  lockedByOther(elementType:UmlLockElementType,elementId:string){const lock=this.lockFor(elementType,elementId);return !!lock&&lock.userId!==this.currentUserId();}
  canEdit(elementType:UmlLockElementType,elementId:string){if(!this.collaborativeSession())return true;return this.lockFor(elementType,elementId)?.userId===this.currentUserId();}
  beginEdit(elementType:UmlLockElementType,elementId:string){this.requestLock(elementType,elementId,()=>this.announceAction(elementId,'está editando','editando'));}
  leave() { this.collaboration.disconnect(); this.router.navigate(['/proyectos']); }
  dialogTitle() { return this.dialogAction()==='delete-class'?'¿Eliminar clase?':this.dialogAction()==='delete-relation'?'¿Eliminar relación?':'¿Restablecer diagrama?'; }
  dialogMessage() { const item=this.selectedClass(); if(this.dialogAction()==='delete-class') return `¿Estás seguro de que deseas eliminar la clase “${item?.name}”?${this.relations().some(r=>r.sourceId===item?.id||r.targetId===item?.id)?' Las relaciones asociadas a esta clase también serán eliminadas.':''}`; if(this.dialogAction()==='delete-relation') return 'Se eliminará únicamente la relación seleccionada.'; return `Se eliminarán las clases y relaciones de “${this.model().title}”. El lienzo quedará en blanco y se conservará el proyecto.`; }
  savedTime() { const date=this.lastSavedAt(); return date ? date.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}) : ''; }

  @HostListener('window:keydown',['$event']) handleShortcut(event:KeyboardEvent){ if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();this.saveProject();} if(event.key==='Escape')this.cancelDialog(); }

  private executeAiCommand(command:UmlAiCommand,done:()=>void){
    const p=command.parameters||{};
    const lock=(type:UmlLockElementType,id:string,action:()=>void)=>{
      const owner=this.lockFor(type,id);
      if(owner&&owner.userId!==this.currentUserId()){this.finishAi(this.aiInstruction(),`Este elemento está siendo editado por ${owner.userName}.`,'error');return;}
      this.requestLock(type,id,action);
    };
    switch(command.operation){
      case'CLASS_CREATE':{
        const name=String(p['name']||'').trim();if(!name)return;
        const count=this.classes().length;
        const attributes:UmlAttribute[]=(Array.isArray(p['attributes'])?p['attributes']:[]).map((value:any)=>({id:String(value.id||this.entityId('attr')),name:String(value.name),type:String(value.type||'String'),isPrimaryKey:value.isPrimaryKey===true}));
        const item:UmlClassNode={id:String(p['id']||this.uniqueId(name)),name,description:'',stereotype:'Entidad',attributes,methods:[],responsibilities:[],x:70+(count%4)*350,y:70+Math.floor(count/4)*300,color:['#1e3a8a','#2563eb','#7c3aed','#b45309'][count%4]};
        this.updateModel({...this.model(),classes:[...this.classes(),item]},'Clase creada mediante IA','class:create',item.id,item);this.selectClass(item);done();break;
      }
      case'CLASS_UPDATE':{const id=command.targetId!;lock('class',id,()=>{const item=this.classes().find(value=>value.id===id);if(!item)return;const updated={...item,...p};this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===id?updated:value)},'Clase actualizada mediante IA','class:update',id,updated,'class');this.releaseLock('class',id);done();});break;}
      case'CLASS_DELETE':{const id=command.targetId!;lock('class',id,()=>{const linkedIds=this.relations().filter(value=>value.sourceId===id||value.targetId===id).map(value=>value.associationClassId).filter((value):value is string=>!!value);this.updateModel({...this.model(),classes:this.classes().filter(value=>value.id!==id&&!linkedIds.includes(value.id)),relations:this.relations().filter(value=>value.sourceId!==id&&value.targetId!==id).map(value=>value.associationClassId===id?{...value,associationClassId:undefined}:value)},'Clase eliminada mediante IA','class:delete',id,undefined,'class');this.releaseLock('class',id);if(this.selectedClassId()===id)this.clearSelection();done();});break;}
      case'ATTRIBUTE_CREATE':{
        const classId=String(p['classId']),attribute:UmlAttribute={id:String(command.targetId||p['id']||this.entityId('attr')),name:String(p['name']),type:String(p['type']||'String'),isPrimaryKey:p['isPrimaryKey']===true};
        lock('attribute',attribute.id,()=>{const clazz=this.classes().find(value=>value.id===classId);if(!clazz)return;this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===classId?{...value,attributes:[...value.attributes,attribute]}:value)},'Atributo creado mediante IA');this.broadcastAttribute('attribute:create',classId,attribute.id,attribute);this.releaseLock('attribute',attribute.id);done();});break;
      }
      case'ATTRIBUTE_UPDATE':{const id=command.targetId!,classId=String(p['classId']);lock('attribute',id,()=>{const clazz=this.classes().find(value=>value.id===classId),current=clazz?.attributes.find(value=>value.id===id);if(!clazz||!current)return;const attribute:UmlAttribute={...current,...(p['name']?{name:String(p['name'])}:{}),...(p['type']?{type:String(p['type'])}:{}),...(typeof p['isPrimaryKey']==='boolean'?{isPrimaryKey:p['isPrimaryKey']}:{}),id};this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===classId?{...value,attributes:value.attributes.map(item=>item.id===id?attribute:item)}:value)},'Atributo actualizado mediante IA');this.broadcastAttribute('attribute:update',classId,id,attribute);this.releaseLock('attribute',id);done();});break;}
      case'ATTRIBUTE_DELETE':{const id=command.targetId!,classId=String(p['classId']);lock('attribute',id,()=>{this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===classId?{...value,attributes:value.attributes.filter(item=>item.id!==id)}:value)},'Atributo eliminado mediante IA');this.broadcastAttribute('attribute:delete',classId,id);this.releaseLock('attribute',id);done();});break;}
      case'METHOD_CREATE':{const classId=String(p['classId']);lock('class',classId,()=>{const clazz=this.classes().find(value=>value.id===classId);if(!clazz)return;const method:UmlMethod={id:String(command.targetId||p['id']||this.entityId('method')),name:String(p['name']),parameters:String(p['parameters']||''),returnType:String(p['returnType']||'void')};const updated={...clazz,methods:[...clazz.methods,method]};this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===classId?updated:value)},'Método creado mediante asistente','class:update',classId,updated,'class');this.releaseLock('class',classId);done();});break;}
      case'METHOD_UPDATE':{const classId=String(p['classId']),id=command.targetId!;lock('class',classId,()=>{const clazz=this.classes().find(value=>value.id===classId);if(!clazz)return;const updated={...clazz,methods:clazz.methods.map(value=>value.id===id?{...value,...(p['name']?{name:String(p['name'])}:{}),...(p['parameters']!==undefined?{parameters:String(p['parameters'])}:{}),...(p['returnType']?{returnType:String(p['returnType'])}:{})}:value)};this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===classId?updated:value)},'Método actualizado mediante asistente','class:update',classId,updated,'class');this.releaseLock('class',classId);done();});break;}
      case'METHOD_DELETE':{const classId=String(p['classId']),id=command.targetId!;lock('class',classId,()=>{const clazz=this.classes().find(value=>value.id===classId);if(!clazz)return;const updated={...clazz,methods:clazz.methods.filter(value=>value.id!==id)};this.updateModel({...this.model(),classes:this.classes().map(value=>value.id===classId?updated:value)},'Método eliminado mediante asistente','class:update',classId,updated,'class');this.releaseLock('class',classId);done();});break;}
      case'RELATION_CREATE':{
        const relation:UmlRelation={id:String(command.targetId||p['id']||this.entityId('rel')),sourceId:String(p['sourceId']),targetId:String(p['targetId']),type:(p['type']||'association') as UmlRelationType,label:String(p['label']||''),sourceMultiplicity:String(p['sourceMultiplicity']||'1'),targetMultiplicity:String(p['targetMultiplicity']||'0..*')};
        const associationClass=this.createAssociationClass(relation,this.classes(),String(p['associationClassName']||''));
        this.updateModel({...this.model(),classes:associationClass?[...this.classes(),associationClass]:this.classes(),relations:[...this.relations(),relation]},'Relación creada mediante IA');if(associationClass)this.broadcast('class:create',associationClass.id,associationClass,'creó una clase de asociación');this.broadcast('relation:create',relation.id,relation,'creó una relación','relation');this.selectedRelationId.set(relation.id);this.selectedClassId.set(null);done();break;
      }
      case'RELATION_UPDATE':{const id=command.targetId!;lock('relation',id,()=>{const current=this.relations().find(value=>value.id===id);if(!current)return;const relation={...current,...p,id};const associationClass=this.createAssociationClass(relation,this.classes(),String(p['associationClassName']||''));this.updateModel({...this.model(),classes:associationClass?[...this.classes(),associationClass]:this.classes(),relations:this.relations().map(value=>value.id===id?relation:value)},'Relación actualizada mediante IA');if(associationClass)this.broadcast('class:create',associationClass.id,associationClass,'creó una clase de asociación');this.broadcast('relation:update',id,relation,'actualizó una relación','relation');this.releaseLock('relation',id);done();});break;}
      case'RELATION_DELETE':{const id=command.targetId!;lock('relation',id,()=>{const associationClassId=this.relations().find(value=>value.id===id)?.associationClassId;this.updateModel({...this.model(),classes:associationClassId?this.classes().filter(value=>value.id!==associationClassId):this.classes(),relations:this.relations().filter(value=>value.id!==id)},'Relación eliminada mediante IA');if(associationClassId)this.broadcast('class:delete',associationClassId,undefined,'eliminó una clase de asociación','class');this.broadcast('relation:delete',id,undefined,'eliminó una relación','relation');this.releaseLock('relation',id);if(this.selectedRelationId()===id)this.clearSelection();done();});break;}
    }
  }
  private executeAiCommands(commands:UmlAiCommand[],index:number,done:()=>void){if(index>=commands.length){done();return;}this.executeAiCommand(commands[index],()=>this.executeAiCommands(commands,index+1,done));}
  private finishAi(instruction:string,message:string,kind:'success'|'error'|'clarification'){this.aiResult.set({kind,text:message});this.aiHistory.update(items=>[{instruction,message,kind,at:new Date()},...items].slice(0,12));if(kind==='success')this.showNotice('success',message);}

  private updateModel(model:UmlDiagramModel,message:string,type?:string,elementId?:string,element?:unknown,elementType?:UmlLockElementType){this.model.set(model);this.persist(message);if(type&&elementId)this.broadcast(type,elementId,element,message,elementType);}
  private persist(message:string){const now=new Date();const model={...this.model(),updatedAt:now.toISOString()};this.model.set(model);this.projectStore.cacheModel(this.projectId,model);this.pendingSaveMessage=message;if(this.saveTimer!==null)window.clearTimeout(this.saveTimer);if(!this.saveInFlight)this.saveTimer=window.setTimeout(()=>this.flushPersist(),350);}
  private flushPersist() {
    if (this.saveInFlight || !this.pendingSaveMessage) return;
    const message = this.pendingSaveMessage;
    const explicit = this.explicitSavePending;
    this.explicitSavePending = false;
    this.pendingSaveMessage = null;
    this.saveTimer = null;
    this.saveInFlight = true;
    this.status.set('Guardando en PostgreSQL...');
    this.projectStore.saveModel(this.projectId, this.model()).subscribe({
      next: project => {
        this.lastSavedAt.set(new Date(project.updatedAt));
        this.status.set(message);
        this.saveInFlight = false;
        if (explicit) {
          this.releaseOwnedLocks();
          this.showNotice('success', 'Proyecto guardado correctamente y edición liberada.');
        }
        if (this.pendingSaveMessage) this.saveTimer = window.setTimeout(() => this.flushPersist(), 50);
      },
      error: error => {
        this.saveInFlight = false;
        const text = error?.status === 409
          ? 'Existe una versión más reciente. Recarga antes de guardar.'
          : 'No se pudo guardar. La copia local permanece disponible.';
        this.status.set(text);
        if (explicit) this.showNotice('error', text);
      }
    });
  }
  private joinByInvitation(code:string){this.collaboration.join(code).subscribe({next:session=>{const model=this.normalizeModel(session.model);this.model.set(model);this.projectStore.upsertCollaborative(session.projectId,session.projectName,model);this.lastSavedAt.set(new Date(model.updatedAt||Date.now()));this.activateSession(session);},error:()=>{this.showNotice('error','La invitación no existe o ya no está activa.');this.router.navigate(['/proyectos']);}});}
  private activateSession(session:UmlSession){this.collaborativeSession.set(session);this.status.set('Sesión colaborativa activa');this.activity.set([]);this.collaboration.activities(session.sessionId).subscribe({next:history=>this.activity.update(live=>{const ids=new Set(live.map(item=>item.eventId));return [...live,...history.filter(item=>!ids.has(item.eventId))].sort((a,b)=>(b.timestamp??0)-(a.timestamp??0)).slice(0,100);}),error:()=>{}});this.collaboration.connect(session,event=>this.handleLiveEvent(event));}
  private broadcast(type:string,elementId:string,element:unknown,operation:string,elementType?:UmlLockElementType){const session=this.collaborativeSession();if(!session)return;this.collaboration.publish({type,projectId:this.projectId,elementId,elementType:elementType??(type.startsWith('relation:')?'relation':'class'),operation,data:{element}});}
  private broadcastAttribute(type:'attribute:create'|'attribute:update'|'attribute:delete',classId:string,attributeId:string,attribute?:UmlAttribute){if(!this.collaborativeSession())return;this.collaboration.publish({type,projectId:this.projectId,elementId:attributeId,elementType:'attribute',operation:type==='attribute:create'?'creó un atributo':type==='attribute:delete'?'eliminó un atributo':'actualizó un atributo',data:{classId,attributeId,...(attribute?{attribute}:{})}});}
  private announceAction(elementId:string,operation:string,action:string){const session=this.collaborativeSession();if(!session)return;this.collaboration.publish({type:'participant:editing',projectId:this.projectId,elementId,operation,data:{element:{action}}});}
  private shareCursor(event:PointerEvent){if(!this.collaborativeSession())return;const now=Date.now();if(now-this.lastCursorSentAt<45)return;this.lastCursorSentAt=now;const rect=this.canvas.nativeElement.getBoundingClientRect();const x=Math.max(0,Math.min(this.canvasWidth,(event.clientX-rect.left)/this.zoom()));const y=Math.max(0,Math.min(this.canvasHeight,(event.clientY-rect.top)/this.zoom()));this.broadcastCursor(x,y,true);}
  private broadcastCursor(x:number,y:number,visible:boolean){const session=this.collaborativeSession();if(!session)return;this.collaboration.publish({type:'participant:cursor',projectId:this.projectId,operation:'moviendo el cursor',data:{element:{x,y,visible}}});}
  private cursorColor(userId:string){const colors=['#2563eb','#7c3aed','#db2777','#1e40af','#ea580c','#0891b2'];let hash=0;for(const char of userId)hash=(hash*31+char.charCodeAt(0))|0;return colors[Math.abs(hash)%colors.length];}
  private collaboratorName(userId:string,eventName?:string){return eventName?.trim()||this.participants().find(participant=>participant.userId===userId)?.userName?.trim()||userId;}
  private handleLiveEvent(event:UmlLiveEvent){
    if(event.type.startsWith('lock:')){this.handleLockEvent(event);return;}
    if(event.data?.participants)this.participants.set(event.data.participants);
    const own=event.userId===this.auth.currentUser()?.username;
    if(!own&&event.type==='participant:cursor'&&event.userId){const cursor=(event.data?.element||{}) as any;if(cursor.visible===false)this.remoteCursors.update(value=>{const next={...value};delete next[event.userId!];return next;});else if(Number.isFinite(cursor.x)&&Number.isFinite(cursor.y))this.remoteCursors.update(value=>({...value,[event.userId!]:{userName:this.collaboratorName(event.userId!,event.userName),x:cursor.x,y:cursor.y,color:this.cursorColor(event.userId!)}}));}
    if(event.type==='session:leave'&&event.userId)this.remoteCursors.update(value=>{const next={...value};delete next[event.userId!];return next;});
    if(!own&&event.type==='participant:editing'&&event.elementId){const action=(event.data?.element as any)?.action||'editando';this.remoteActions.update(value=>({...value,[event.elementId!]:{userName:event.userName||'Usuario',action}}));window.setTimeout(()=>this.remoteActions.update(value=>{const next={...value};delete next[event.elementId!];return next;}),2500);}
    if(!own)this.applyRemoteMutation(event);
    if(event.operation&&event.type!=='participant:cursor'&&!event.type.endsWith(':error'))this.activity.update(items=>[event,...items].slice(0,100));
    if(event.type==='session:error')this.showNotice('error',event.operation||'Error de colaboración');
  }
  private requestLock(elementType:UmlLockElementType,elementId:string,onGranted?:()=>void){if(!this.collaborativeSession()){onGranted?.();return;}const key=this.lockKey(elementType,elementId),current=this.elementLocks()[key];if(current?.userId===this.currentUserId()){onGranted?.();return;}if(current){this.lockBlocked(elementType,elementId);return;}if(this.pendingLock()===key){if(onGranted)this.pendingLockActions.set(key,onGranted);return;}if(onGranted)this.pendingLockActions.set(key,onGranted);this.pendingLock.set(key);this.collaboration.publish({type:'lock:acquire',projectId:this.projectId,elementId,elementType,operation:'solicita editar'});}
  private releaseLock(elementType:UmlLockElementType,elementId:string){if(!this.collaborativeSession()||!this.canEdit(elementType,elementId))return;this.collaboration.publish({type:'lock:release',projectId:this.projectId,elementId,elementType,operation:'finalizó la edición'});}
  private releaseOwnedLocks(){Object.values(this.elementLocks()).filter(lock=>lock.userId===this.currentUserId()).forEach(lock=>this.releaseLock(lock.elementType,lock.elementId));}
  private renewOwnedLocks(){if(!this.collaborativeSession())return;Object.values(this.elementLocks()).filter(lock=>lock.userId===this.currentUserId()).forEach(lock=>this.collaboration.publish({type:'lock:renew',projectId:this.projectId,elementId:lock.elementId,elementType:lock.elementType,operation:'continúa editando'}));}
  private beginDragging(event:PointerEvent,item:UmlClassNode){event.preventDefault();this.announceAction(item.id,`está moviendo ${item.name}`,'moviendo');const rect=this.canvas.nativeElement.getBoundingClientRect();this.dragging={id:item.id,offsetX:(event.clientX-rect.left)/this.zoom()-item.x,offsetY:(event.clientY-rect.top)/this.zoom()-item.y};(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);}
  private handleLockEvent(event:UmlLiveEvent){const lock=event.data?.lock,key=this.lockKey(event.elementType!,event.elementId!);if(event.type==='lock:granted'&&lock){this.elementLocks.update(value=>({...value,[key]:lock}));if(event.data?.requesterId===this.currentUserId()){this.pendingLock.set(null);const action=this.pendingLockActions.get(key);this.pendingLockActions.delete(key);action?.();}}else if(event.type==='lock:denied'){if(lock)this.elementLocks.update(value=>({...value,[key]:lock}));if(event.data?.requesterId===this.currentUserId()){this.pendingLock.set(null);this.pendingLockActions.delete(key);this.showNotice('error',`Este elemento está siendo editado por ${lock?.userName||event.userName||'otro usuario'}.`);}}else if(event.type==='lock:released'||event.type==='lock:expired'){this.elementLocks.update(value=>{const next={...value};delete next[key];return next;});}if(event.operation)this.activity.update(items=>[event,...items].slice(0,12));}
  private lockBlocked(elementType:UmlLockElementType,elementId:string){const owner=this.lockFor(elementType,elementId)?.userName;if(owner)this.showNotice('error',`Este elemento está siendo editado por ${owner}.`);else if(this.collaborativeSession())this.showNotice('error','Espera a que se conceda el bloqueo de edición.');}
  private lockKey(elementType:UmlLockElementType,elementId:string){return `${elementType}:${elementId}`;}
  private currentUserId(){return this.auth.currentUser()?.username??'';}
  activityTarget(event:UmlLiveEvent){const labels:Record<string,string>={class:'Clase',attribute:'Atributo',relation:'Relación'};const element=(event.data?.element||event.data?.attribute) as any;const name=element?.name||element?.label;return event.elementType?`${labels[event.elementType]||event.elementType}: ${name||event.elementId||'sin identificar'}`:'';}
  private applyRemoteMutation(event:UmlLiveEvent){const element=(event.data?.element||null) as any;let classes=this.classes(),relations=this.relations();switch(event.type){case'class:create':case'class:update':case'class:move':if(element)classes=[...classes.filter(i=>i.id!==element.id),element];break;case'attribute:create':{const classId=event.data?.classId,attribute=event.data?.attribute as UmlAttribute|undefined;if(!classId||!attribute)return;classes=classes.map(item=>item.id===classId?{...item,attributes:item.attributes.some(value=>value.id===attribute.id)?item.attributes:[...item.attributes,attribute]}:item);break;}case'attribute:update':{const classId=event.data?.classId,attributeId=event.data?.attributeId,attribute=event.data?.attribute as UmlAttribute|undefined;if(!classId||!attributeId||!attribute)return;classes=classes.map(item=>item.id===classId?{...item,attributes:item.attributes.map(value=>value.id===attributeId?attribute:value)}:item);break;}case'attribute:delete':{const classId=event.data?.classId,attributeId=event.data?.attributeId;if(!classId||!attributeId)return;classes=classes.map(item=>item.id===classId?{...item,attributes:item.attributes.filter(value=>value.id!==attributeId)}:item);break;}case'class:delete':{const linkedIds=relations.filter(r=>r.sourceId===event.elementId||r.targetId===event.elementId).map(r=>r.associationClassId).filter((value):value is string=>!!value);classes=classes.filter(i=>i.id!==event.elementId&&!linkedIds.includes(i.id));relations=relations.filter(r=>r.sourceId!==event.elementId&&r.targetId!==event.elementId).map(r=>r.associationClassId===event.elementId?{...r,associationClassId:undefined}:r);break;}case'relation:create':case'relation:update':if(element)relations=[...relations.filter(i=>i.id!==element.id),element];break;case'relation:delete':relations=relations.filter(i=>i.id!==event.elementId);break;default:return;}this.model.set({...this.model(),classes,relations,updatedAt:new Date().toISOString()});this.projectStore.upsertCollaborative(this.projectId,this.model().title,this.model());this.persist(`${event.userName||'Otro usuario'} ${event.operation||'actualizó el diagrama'}`);}
  private normalizeModel(value:any):UmlDiagramModel { if(value?.format!=='uml-class-diagram'||![1,2].includes(value?.version)||!Array.isArray(value.classes)||!Array.isArray(value.relations)) throw new Error('El modelo UML almacenado tiene un formato incompatible.'); const classes=value.classes.map((item:any)=>({...item,description:item.description??item.responsibilities?.[0]??'',responsibilities:Array.isArray(item.responsibilities)?item.responsibilities:[],attributes:(item.attributes??[]).map((a:any,n:number)=>typeof a==='string'?this.parseLegacyAttribute(item.id,a,n):({...a,isPrimaryKey:a.isPrimaryKey===true})),methods:Array.isArray(item.methods)?item.methods:[],color:item.color||'#2563eb'}));const classIds=new Set(classes.map((item:any)=>item.id));const relations=value.relations.filter((relation:any)=>classIds.has(relation.sourceId)&&classIds.has(relation.targetId));return {...value,version:2,classes,relations}; }
  private parseLegacyAttribute(classId:string,value:string,index:number):UmlAttribute{const split=value.indexOf(':');const name=(split<0?value:value.slice(0,split)).trim();return{id:`${classId}-attr-${index+1}`,name,type:(split<0?'String':value.slice(split+1)).trim()||'String',isPrimaryKey:false};}
  private emptyAttribute():UmlAttribute{return{id:'',name:'',type:'String',isPrimaryKey:false};} private emptyMethod():UmlMethod{return{id:'',name:'',parameters:'',returnType:'void'};}
  private validMultiplicity(value:string){return /^(\*|\d+|\d+\.\.(\d+|\*))$/.test(value.trim());}
  private showNotice(kind:'error'|'success',text:string){this.notice.set({kind,text});window.setTimeout(()=>{if(this.notice()?.text===text)this.notice.set(null);},3500);}
  private uniqueId(name:string){const base=name.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')||'clase';let id=base,n=2;while(this.classes().some(i=>i.id===id))id=`${base}-${n++}`;return id;}
  private entityId(prefix:string){return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2,7)}`;}
  private safeTitle(){return this.model().title.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')||'diagrama-clases';}
  private download(filename:string,content:string,mime:string){const url=URL.createObjectURL(new Blob([content],{type:mime})),a=document.createElement('a');a.href=url;a.download=filename;a.click();URL.revokeObjectURL(url);}
  private clone<T>(value:T):T{return JSON.parse(JSON.stringify(value));}
}
