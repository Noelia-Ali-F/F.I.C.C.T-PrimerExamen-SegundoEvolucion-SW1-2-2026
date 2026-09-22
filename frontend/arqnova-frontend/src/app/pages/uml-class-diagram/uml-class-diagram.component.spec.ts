import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { UmlClassDiagramComponent } from './uml-class-diagram.component';
import { DEFAULT_UML_MODEL } from './uml-class-diagram.models';
import { BASE_PATH } from '../../api/variables';
import { of, Subject } from 'rxjs';
import { UmlProjectStoreService } from './uml-project-store.service';
import { UmlAiCommandService } from './uml-ai-command.service';
import { UmlXmiService } from './uml-xmi.service';
import { vi } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';

describe('UmlClassDiagramComponent', () => {
  let component: UmlClassDiagramComponent;
  let fixture: ComponentFixture<UmlClassDiagramComponent>;

  beforeEach(async () => {
    localStorage.clear();
    localStorage.setItem('uml-projects-v1', JSON.stringify([{ id: 'project-test', name: 'Prueba', description: '', status: 'EN_EDICION', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), model: DEFAULT_UML_MODEL }]));
    await TestBed.configureTestingModule({
      imports: [UmlClassDiagramComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        { provide: BASE_PATH, useValue: 'http://localhost:8080' },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: (key: string) => key === 'projectId' ? 'project-test' : null }, queryParamMap: { get: () => null } } } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(UmlClassDiagramComponent);
    component = fixture.componentInstance;
    component.model.set({ format: 'uml-class-diagram', version: 2, title: 'Prueba', classes: [], relations: [] });
  });

  it('CU9 muestra la opción para generar backend Spring Boot y abre su configuración', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Generar Backend');
    component.openBackendGenerator();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Generar backend Spring Boot');
    expect(fixture.nativeElement.textContent).toContain('entidades JPA');
    expect(component.backendConfig.basePackage).toMatch(/^com\.generated\./);
  });

  it('CU12 abre un generador Flutter independiente con URL backend configurable', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Generar Flutter');
    component.openFlutterGenerator();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Generar frontend Flutter');
    expect(fixture.nativeElement.textContent).toContain('modelos Dart');
    expect(component.flutterConfig.backendBaseUrl).toBe('http://10.0.2.2:8080');
    expect(component.generatedBackend()).toBeNull();
  });

  it('crea una clase y la selecciona', () => {
    component.newClassName = 'Usuario';
    component.addClass();
    expect(component.classes()[0].name).toBe('Usuario');
    expect(component.selectedClass()?.name).toBe('Usuario');
  });

  it('CU7 renderiza usuario, acción, elemento y hora de una actividad colaborativa', () => {
    component.collaborativeSession.set({ sessionId: 's1', projectId: 'project-test', projectName: 'Prueba', inviteCode: 'ABC', model: component.model() });
    (component as any).handleLiveEvent({ eventId: 'event-1', type: 'class:create', projectId: 'project-test', sessionId: 's1', userId: 'ana', userName: 'Ana Gómez', elementId: 'c1', elementType: 'class', operation: 'creó una clase', timestamp: Date.now(), data: { element: { id: 'c1', name: 'Cliente', attributes: [], methods: [], x: 10, y: 10 } } });
    fixture.detectChanges();

    const text = fixture.nativeElement.querySelector('.activity-list').textContent;
    expect(text).toContain('Ana Gómez');
    expect(text).toContain('creó una clase');
    expect(text).toContain('Clase: Cliente');
  });

  it('restablece en blanco el proyecto actual sin cargar el ejemplo ni cambiar su nombre', () => {
    component.model.set({...DEFAULT_UML_MODEL,title:'hospital'});
    component.selectClass(component.classes()[0]);
    component.requestReset();
    expect(component.dialogMessage()).toContain('hospital');
    component.confirmDialog();
    expect(component.model().title).toBe('hospital');
    expect(component.classes()).toEqual([]);
    expect(component.relations()).toEqual([]);
    expect(component.selectedClass()).toBeNull();
    const stored=JSON.parse(localStorage.getItem('uml-projects-v1')!);
    expect(stored[0].id).toBe('project-test');
    expect(stored[0].model.classes).toEqual([]);
    expect(stored[0].model.title).toBe('hospital');
  });

  it('cancelar restablecer conserva el diagrama', () => {
    component.model.set({...DEFAULT_UML_MODEL,title:'hospital'});
    const before=component.model();
    component.requestReset(); component.cancelDialog();
    expect(component.model()).toEqual(before);
  });

  it('CU8 preview no modifica; confirmar inserta UML editable conservando proyecto y cache', () => {
    const source={...DEFAULT_UML_MODEL,title:'Archivo externo',classes:DEFAULT_UML_MODEL.classes.slice(0,2),relations:[]};
    const preview=TestBed.inject(UmlXmiService).parse(TestBed.inject(UmlXmiService).serialize(source));
    component.xmiPreview.set(preview);
    expect(component.classes()).toHaveLength(0);
    component.confirmXmiImport();
    expect(component.classes()).toHaveLength(2);
    expect(component.model().title).toBe('Prueba');
    component.selectClass(component.classes()[0]);
    component.editAttribute(component.selectedClass()!.attributes[0]);
    component.attributeDraft.type='UUID'; component.saveAttribute();
    expect(component.selectedClass()!.attributes[0].type).toBe('UUID');
    expect(JSON.parse(localStorage.getItem('uml-projects-v1')!)[0].model.classes).toHaveLength(2);
    component.xmiPreview.set(preview);component.confirmXmiImport();
    expect(component.classes()).toHaveLength(2);
  });

  for (const code of ['AI_PROVIDER_UNAVAILABLE','AI_RATE_LIMITED','NO_UML_DETECTED','INVALID_IMAGE','AI_INVALID_RESPONSE']) {
    it(`CU7 ${code} detiene loading y no modifica ni guarda el diagrama`, () => {
      const result=new Subject<any>();
      vi.spyOn(TestBed.inject(UmlAiCommandService),'analyzeImage').mockReturnValue(result);
      const save=vi.spyOn(TestBed.inject(UmlProjectStoreService),'saveModel');
      component.imageFile=new File(['test'],'uml.png',{type:'image/png'});
      const before=component.model();
      component.processUmlImage();
      result.error({error:{code},status:503});
      expect(component.imageProcessing()).toBe(false);
      expect(component.imageError()).toBeTruthy();
      expect(component.model()).toEqual(before);
      expect(component.imageFile).not.toBeNull();
      expect(save).not.toHaveBeenCalled();
      expect(component.imageAnalysis()).toBeNull();
    });
  }

  it('confirma guardado solo después de la respuesta del backend', () => {
    const result = new Subject<any>();
    const save = vi.spyOn(TestBed.inject(UmlProjectStoreService), 'saveModel').mockReturnValue(result);
    component.saveProject();
    expect(save).toHaveBeenCalledTimes(1);
    expect(component.notice()?.kind).not.toBe('success');
    result.next({updatedAt:new Date().toISOString(),modelVersion:1});
    expect(component.notice()?.kind).toBe('success');
  });

  for (const status of [500,409]) {
    it(`no anuncia éxito ante HTTP ${status} y conserva el modelo`, () => {
      const result = new Subject<any>();
      vi.spyOn(TestBed.inject(UmlProjectStoreService), 'saveModel').mockReturnValue(result);
      const before = component.model();
      component.saveProject();
      result.error({status});
      expect(component.notice()?.kind).toBe('error');
      expect(component.model().classes).toEqual(before.classes);
      if(status===409) expect(component.status()).toContain('versión más reciente');
    });
  }

  it('gestiona atributos y métodos estructurados', () => {
    component.newClassName = 'Usuario'; component.addClass();
    component.attributeDraft = { id: '', name: 'id', type: 'String', isPrimaryKey: true }; component.saveAttribute();
    component.methodDraft = { id: '', name: 'autenticar', parameters: '', returnType: 'Boolean' }; component.saveMethod();
    expect(component.selectedClass()?.attributes[0].name).toBe('id');
    expect(component.selectedClass()?.attributes[0].isPrimaryKey).toBe(true);
    expect(component.selectedClass()?.methods[0].returnType).toBe('Boolean');
    component.editAttribute(component.selectedClass()!.attributes[0]);
    component.attributeDraft.isPrimaryKey = false;
    component.saveAttribute();
    expect(component.selectedClass()?.attributes[0].isPrimaryKey).toBe(false);
    component.deleteAttribute(component.selectedClass()!.attributes[0].id);
    component.deleteMethod(component.selectedClass()!.methods[0].id);
    expect(component.selectedClass()?.attributes).toHaveLength(0);
    expect(component.selectedClass()?.methods).toHaveLength(0);
  });

  it('usa String por defecto y limita la edición manual a tipos compatibles con CU9', () => {
    component.newClassName='Profesional'; component.addClass();
    expect(component.attributeDraft.type).toBe('String');
    component.attributeDraft.name='email'; component.attributeDraft.type='UUID'; component.saveAttribute();
    expect(component.selectedClass()!.attributes[0].type).toBe('UUID');
    component.editAttribute(component.selectedClass()!.attributes[0]);
    component.attributeDraft.type='varchar'; component.saveAttribute();
    expect(component.notice()?.text).toBe('Tipo de atributo no soportado. Seleccione un tipo UML compatible.');
    expect(component.selectedClass()!.attributes[0].type).toBe('UUID');
    expect(component.editingAttributeId).toBe(component.selectedClass()!.attributes[0].id);
  });

  it('elimina una clase y todas sus relaciones sin borrar la otra clase', () => {
    component.newClassName = 'Usuario'; component.addClass(); const userId = component.selectedClass()!.id;
    component.newClassName = 'Solicitud'; component.addClass(); const requestId = component.selectedClass()!.id;
    component.relationDraft = { from: userId, to: requestId, type: 'association', label: 'crea', fromMultiplicity: '1', toMultiplicity: '0..*' };
    component.addRelation(); component.selectClass(component.classes().find(item => item.id === userId)!);
    component.requestDeleteClass(); component.confirmDialog();
    expect(component.classes().map(item => item.name)).toEqual(['Solicitud']);
    expect(component.relations()).toHaveLength(0);
  });

  it('persiste posiciones y el modelo completo', () => {
    component.newClassName = 'Solicitud'; component.addClass();
    component.updateSelectedClass({ x: 720, y: 240 }); component.saveProject();
    const stored = JSON.parse(localStorage.getItem('uml-projects-v1')!);
    expect(stored[0].model.classes[0].x).toBe(720);
    expect(stored[0].model.classes[0].y).toBe(240);
    expect(stored[0].model.version).toBe(2);
  });

  it('cumple el flujo CU4 con tres clases, PK, relaciones editables y eliminación independiente', () => {
    for (const name of ['Usuario', 'Libro', 'Prestamo']) { component.newClassName = name; component.addClass(); }
    const [usuario, libro, prestamo] = component.classes();

    component.selectClass(usuario);
    component.attributeDraft = { id: '', name: 'id', type: 'Long', isPrimaryKey: true };
    component.saveAttribute();
    const userAttribute = component.selectedClass()!.attributes[0];
    component.editAttribute(userAttribute);
    component.attributeDraft.type = 'UUID';
    component.saveAttribute();
    expect(component.selectedClass()!.attributes[0]).toEqual(expect.objectContaining({ type: 'UUID', isPrimaryKey: true }));

    component.updateSelectedClass({ x: 120, y: 180 });
    component.relationDraft = { from: usuario.id, to: libro.id, type: 'association', label: 'consulta', fromMultiplicity: '1', toMultiplicity: '0..*' };
    component.addRelation();
    component.updateSelectedRelation({ targetId: prestamo.id, label: 'realiza', targetMultiplicity: '1..*' });
    expect(component.selectedRelation()).toEqual(expect.objectContaining({ sourceId: usuario.id, targetId: prestamo.id, label: 'realiza', targetMultiplicity: '1..*' }));

    component.requestDeleteRelation(); component.confirmDialog();
    expect(component.relations()).toHaveLength(0);
    expect(component.classes()).toHaveLength(3);

    component.relationDraft = { from: usuario.id, to: prestamo.id, type: 'association', label: 'crea', fromMultiplicity: '1', toMultiplicity: '0..*' };
    component.addRelation();
    component.selectClass(prestamo); component.requestDeleteClass(); component.confirmDialog();
    expect(component.classes().map(item => item.name)).toEqual(['Usuario', 'Libro']);
    expect(component.relations()).toHaveLength(0);

    component.saveProject();
    const stored = JSON.parse(localStorage.getItem('uml-projects-v1')!)[0].model;
    expect(stored.classes.find((item: any) => item.id === usuario.id).x).toBe(120);
    expect(stored.classes.find((item: any) => item.id === usuario.id).attributes[0].isPrimaryKey).toBe(true);
  });

  it('actualiza multiplicidades desde el Inspector y rechaza valores no UML', () => {
    component.newClassName = 'Cliente'; component.addClass(); const cliente = component.selectedClass()!;
    component.newClassName = 'Pedido'; component.addClass(); const pedido = component.selectedClass()!;
    component.relationDraft = { from: cliente.id, to: pedido.id, type: 'association', label: 'realiza', fromMultiplicity: '1', toMultiplicity: '0..*' };
    component.addRelation(); fixture.detectChanges();

    const multiplicities = fixture.nativeElement.querySelectorAll('.inspector-section .coordinate-grid input') as NodeListOf<HTMLInputElement>;
    multiplicities[1].value = '1..*';
    multiplicities[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(component.selectedRelation()?.targetMultiplicity).toBe('1..*');

    multiplicities[1].value = 'muchos';
    multiplicities[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(component.selectedRelation()?.targetMultiplicity).toBe('1..*');
    expect(component.notice()?.text).toContain('Usa multiplicidades');
  });

  it('normaliza atributos almacenados legacy sin inferir PK por el nombre', () => {
    const legacy = {
      format: 'uml-class-diagram', version: 1, title: 'Legacy',
      classes: [{ id: 'usuario', name: 'Usuario', attributes: ['id: String', 'ID: Long', 'Id: UUID'], methods: [], responsibilities: [], x: 10, y: 20, color: '#2563eb' }],
      relations: []
    };
    const normalized = (component as any).normalizeModel(legacy);
    expect(normalized.classes[0].attributes.map((attribute: any) => attribute.isPrimaryKey)).toEqual([false, false, false]);
  });

  it('la barra ofrece únicamente XMI como interoperabilidad visible', () => {
    const fixture = TestBed.createComponent(UmlClassDiagramComponent);
    fixture.componentInstance.model.set(component.model());
    fixture.detectChanges();
    const toolbar: HTMLElement = fixture.nativeElement.querySelector('.toolbar');
    const labels = Array.from(toolbar.querySelectorAll('button')).map(button => button.textContent?.trim());
    expect(toolbar.textContent).not.toContain('Importar JSON');
    expect(toolbar.textContent).not.toContain('Exportar JSON');
    expect(toolbar.textContent).not.toContain('Modelo JSON');
    expect(toolbar.textContent).not.toContain('SVG');
    expect(toolbar.textContent).not.toContain('PlantUML');
    expect(labels.some(label => label?.endsWith('Importar XMI'))).toBe(true);
    expect(labels.some(label => label?.endsWith('Exportar XMI'))).toBe(true);
    expect(toolbar.querySelector('input[accept*=".json"]')).toBeNull();
    expect(toolbar.querySelector('input[accept=".xmi,.xml"]')).not.toBeNull();
    expect((component as any).importJson).toBeUndefined();
    expect((component as any).exportJson).toBeUndefined();
    expect((component as any).exportSvg).toBeUndefined();
    expect((component as any).exportPlantUml).toBeUndefined();
    fixture.destroy();
  });

  it('conserva PK explícita y autorrelaciones válidas al normalizar', () => {
    const source = {
      format: 'uml-class-diagram', version: 2, title: 'Autorrelación',
      classes: [{ id: 'categoria', name: 'Categoria', attributes: [{ id: 'a1', name: 'codigo', type: 'String', isPrimaryKey: true }], methods: [], responsibilities: [], x: 10, y: 20, color: '#2563eb' }],
      relations: [{ id: 'r1', sourceId: 'categoria', targetId: 'categoria', type: 'association', label: 'padre', sourceMultiplicity: '0..1', targetMultiplicity: '0..*' }]
    };

    const normalized = (component as any).normalizeModel(source);

    expect(normalized.classes[0].attributes[0].isPrimaryKey).toBe(true);
    expect(normalized.relations).toHaveLength(1);
    expect(normalized.relations[0]).toEqual(expect.objectContaining({ sourceId: 'categoria', targetId: 'categoria' }));
  });

  it('aplica concesión, rechazo y liberación de bloqueos sin reemplazar el diagrama', () => {
    component.newClassName = 'Usuario'; component.addClass();
    const item = component.selectedClass()!;
    component.collaborativeSession.set({ sessionId: 's1', projectId: 'project-test', projectName: 'Prueba', inviteCode: 'INVITE', model: component.model() });
    (component as any).currentUserId = () => 'maria';
    const mariaLock = { projectId: 'project-test', sessionId: 's1', elementId: item.id, elementType: 'class', userId: 'maria', userName: 'María López', lockedAt: 1, expiresAt: 5000 };

    (component as any).handleLiveEvent({ type: 'lock:granted', projectId: 'project-test', elementId: item.id, elementType: 'class', userId: 'maria', data: { lock: mariaLock, requesterId: 'maria' } });
    expect(component.canEdit('class', item.id)).toBe(true);
    component.updateSelectedClass({ name: 'Cliente' });
    expect(component.selectedClass()?.name).toBe('Cliente');

    (component as any).handleLiveEvent({ type: 'lock:released', projectId: 'project-test', elementId: item.id, elementType: 'class', userId: 'maria', data: { lock: mariaLock } });
    expect(component.lockFor('class', item.id)).toBeUndefined();
    component.updateSelectedClass({ name: 'Cambio sin bloqueo' });
    expect(component.selectedClass()?.name).toBe('Cliente');
  });

  it('muestra al anfitrión y actualiza participantes cuando entran o salen', () => {
    component.collaborativeSession.set({ sessionId: 's1', projectId: 'project-test', projectName: 'Prueba', inviteCode: 'INVITE', hostUserId: 'admin', memberRole: 'EDITOR', model: component.model() });
    (component as any).handleLiveEvent({
      eventId: 'join-maria', type: 'session:join', projectId: 'project-test', userId: 'maria', userName: 'María López', operation: 'se unió a la sesión',
      data: { participants: [
        { userId: 'admin', userName: 'Andrés Pérez', status: 'online', host: true },
        { userId: 'maria', userName: 'María López', status: 'online', host: false }
      ] }
    });
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.collaboration-panel') as HTMLElement;
    expect(component.sessionHostName()).toBe('Andrés Pérez');
    expect(panel.textContent).toContain('Sesión colaborativa');
    expect(panel.textContent).toContain('Andrés Pérez');
    expect(panel.textContent).toContain('Anfitrión');
    expect(panel.textContent).toContain('María López');
    expect(panel.textContent).toContain('Colaborador');

    (component as any).handleLiveEvent({
      eventId: 'leave-maria', type: 'session:leave', projectId: 'project-test', userId: 'maria', userName: 'María López', operation: 'salió de la sesión',
      data: { participants: [{ userId: 'admin', userName: 'Andrés Pérez', status: 'online', host: true }] }
    });
    fixture.detectChanges();
    expect(component.participants().map(item => item.userId)).toEqual(['admin']);
    expect((panel.querySelector('.participant-list') as HTMLElement).textContent).not.toContain('María López');
  });

  it('recupera el cursor remoto con nombre completo, posición y color identificador', () => {
    component.collaborativeSession.set({ sessionId: 's1', projectId: 'project-test', projectName: 'Prueba', inviteCode: 'INVITE', hostUserId: 'admin', memberRole: 'EDITOR', model: component.model() });
    component.participants.set([
      { userId: 'admin', userName: 'Carlos Pérez', status: 'online', host: true },
      { userId: 'ana', userName: 'Ana Gómez', status: 'online', host: false }
    ]);
    (component as any).currentUserId = () => 'admin';

    (component as any).handleLiveEvent({
      eventId: 'cursor-ana', type: 'participant:cursor', projectId: 'project-test', userId: 'ana',
      data: { element: { x: 245, y: 160, visible: true } }
    });
    fixture.detectChanges();

    const cursor = fixture.nativeElement.querySelector('.remote-cursor[data-user-id="ana"]') as HTMLElement;
    expect(cursor).toBeTruthy();
    expect(cursor.querySelector('strong')?.textContent).toContain('Ana Gómez');
    expect(cursor.style.left).toBe('245px');
    expect(cursor.style.top).toBe('160px');
    expect(cursor.style.getPropertyValue('--cursor-color')).toBeTruthy();

    (component as any).handleLiveEvent({
      eventId: 'cursor-ana-hidden', type: 'participant:cursor', projectId: 'project-test', userId: 'ana',
      data: { element: { x: 0, y: 0, visible: false } }
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.remote-cursor[data-user-id="ana"]')).toBeNull();

    (component as any).currentUserId = () => 'ana';
    (component as any).handleLiveEvent({
      eventId: 'cursor-admin', type: 'participant:cursor', projectId: 'project-test', userId: 'admin', userName: 'Carlos Pérez',
      data: { element: { x: 110, y: 90, visible: true } }
    });
    fixture.detectChanges();
    const hostCursor = fixture.nativeElement.querySelector('.remote-cursor[data-user-id="admin"]') as HTMLElement;
    expect(hostCursor.querySelector('strong')?.textContent).toContain('Carlos Pérez');
    expect(hostCursor.style.left).toBe('110px');
    expect(hostCursor.style.top).toBe('90px');
  });

  it('aplica create, update y delete de atributos sin reemplazar la clase completa', () => {
    component.newClassName = 'Usuario'; component.addClass();
    const classId = component.selectedClass()!.id;
    component.attributeDraft = { id: 'attr-id', name: 'id', type: 'Long', isPrimaryKey: true }; component.saveAttribute();
    component.attributeDraft = { id: 'attr-correo', name: 'correo', type: 'String', isPrimaryKey: false }; component.saveAttribute();
    component.updateSelectedClass({ x: 321, description: 'Debe conservarse' });

    (component as any).applyRemoteMutation({ type: 'attribute:update', projectId: 'project-test', elementId: 'attr-id', elementType: 'attribute', data: { classId, attributeId: 'attr-id', attribute: { id: 'attr-id', name: 'id', type: 'UUID', isPrimaryKey: true } } });
    (component as any).applyRemoteMutation({ type: 'attribute:update', projectId: 'project-test', elementId: 'attr-correo', elementType: 'attribute', data: { classId, attributeId: 'attr-correo', attribute: { id: 'attr-correo', name: 'correo', type: 'Email', isPrimaryKey: false } } });
    (component as any).applyRemoteMutation({ type: 'attribute:create', projectId: 'project-test', elementId: 'attr-name', elementType: 'attribute', data: { classId, attributeId: 'attr-name', attribute: { id: 'attr-name', name: 'nombre', type: 'String', isPrimaryKey: false } } });
    (component as any).applyRemoteMutation({ type: 'attribute:delete', projectId: 'project-test', elementId: 'attr-name', elementType: 'attribute', data: { classId, attributeId: 'attr-name' } });

    expect(component.selectedClass()?.attributes.map(item => [item.id, item.type])).toEqual([['attr-id', 'UUID'], ['attr-correo', 'Email']]);
    expect(component.selectedClass()).toEqual(expect.objectContaining({ x: 321, description: 'Debe conservarse' }));
  });

  it('aplica operaciones IA puntuales y persiste sin regenerar el diagrama', () => {
    (component as any).executeAiCommand({ status: 'READY', operation: 'CLASS_CREATE', parameters: { name: 'Cliente', attributes: [] }, message: 'ok' }, () => {});
    const cliente = component.classes()[0];
    (component as any).executeAiCommand({ status: 'READY', operation: 'ATTRIBUTE_CREATE', parameters: { classId: cliente.id, name: 'correo', type: 'String' }, message: 'ok' }, () => {});
    expect(component.classes()).toHaveLength(1);
    expect(component.classes()[0].attributes[0]).toEqual(expect.objectContaining({ name: 'correo', type: 'String' }));
    expect(JSON.parse(localStorage.getItem('uml-projects-v1')!)[0].model.classes[0].attributes[0].name).toBe('correo');
  });

  it('crea, edita y elimina métodos interpretados por el fallback', () => {
    component.newClassName = 'Usuario'; component.addClass();
    const classId = component.selectedClass()!.id;
    (component as any).executeAiCommand({ status: 'READY', operation: 'METHOD_CREATE', parameters: { classId, name: 'autenticar', parameters: 'correo: String, clave: String', returnType: 'Boolean' }, message: 'ok' }, () => {});
    const method = component.selectedClass()!.methods[0];
    expect(method).toEqual(expect.objectContaining({ name: 'autenticar', parameters: 'correo: String, clave: String', returnType: 'Boolean' }));
    (component as any).executeAiCommand({ status: 'READY', operation: 'METHOD_UPDATE', targetId: method.id, parameters: { classId, name: 'validarCredenciales' }, message: 'ok' }, () => {});
    expect(component.selectedClass()!.methods[0].name).toBe('validarCredenciales');
    (component as any).executeAiCommand({ status: 'READY', operation: 'METHOD_DELETE', targetId: method.id, parameters: { classId }, message: 'ok' }, () => {});
    expect(component.selectedClass()!.methods).toHaveLength(0);
    expect(JSON.parse(localStorage.getItem('uml-projects-v1')!)[0].model.classes[0].methods).toHaveLength(0);
  });

  it('inserta el reconocimiento de imagen como UML editable y lo persiste sin sobrescribir', () => {
    component.newClassName = 'Usuario'; component.addClass();
    component.imageModalOpen.set(true);
    component.imageAnalysis.set({ status: 'READY', provider: 'GEMINI', model: 'gemini-3.8-flash', message: 'ok', warnings: [], classes: [
      { name: 'Usuario', attributes: [{ name: 'duplicado', type: 'String', isPrimaryKey: false }] },
      { name: 'Pedido', attributes: [{ name: 'id', type: 'Long', isPrimaryKey: true }, { name: 'fecha', type: 'Date', isPrimaryKey: false }] }
    ], relations: [{ sourceClassName: 'Usuario', targetClassName: 'Pedido', type: 'association', label: 'realiza', sourceMultiplicity: '1', targetMultiplicity: '0..*' }] });

    component.generateImageResult();

    expect(component.classes().map(item => item.name)).toEqual(['Usuario', 'Pedido']);
    expect(component.classes()[0].attributes).toHaveLength(0);
    expect(component.classes()[1].attributes[0]).toEqual(expect.objectContaining({ name: 'id', type: 'Long', isPrimaryKey: true }));
    expect(component.relations()[0]).toEqual(expect.objectContaining({ sourceId: component.classes()[0].id, targetId: component.classes()[1].id, sourceMultiplicity: '1', targetMultiplicity: '0..*' }));
    component.selectClass(component.classes()[1]); component.editAttribute(component.selectedClass()!.attributes[1]); component.attributeDraft.type='Date'; component.saveAttribute();
    component.selectRelation(component.relations()[0], new Event('click')); component.updateSelectedRelation({ targetMultiplicity: '1..*' });
    const stored=JSON.parse(localStorage.getItem('uml-projects-v1')!)[0].model;
    expect(stored.classes.find((item:any)=>item.name==='Pedido').attributes[1].type).toBe('Date');
    expect(stored.relations[0].targetMultiplicity).toBe('1..*');
  });

  it('CU7 conserva ocho atributos sin tipo al insertar y normalizar, editables en Inspector', () => {
    component.imageAnalysis.set({status:'READY',provider:'GEMINI',model:'test',message:'ok',warnings:[],classes:[
      {name:'Paciente',attributes:['Id','nombre','Documento','Telefono'].map(name=>({name,type:'',isPrimaryKey:false}))},
      {name:'Turno',attributes:['Id','FechaHora','Estado','motivo'].map(name=>({name,type:'',isPrimaryKey:false}))}
    ],relations:[{sourceClassName:'Paciente',targetClassName:'Turno',type:'association',label:'reserva',sourceMultiplicity:'1',targetMultiplicity:'0..*'}]});
    component.generateImageResult();
    expect(component.classes().flatMap(item=>item.attributes)).toHaveLength(8);
    expect(component.classes().flatMap(item=>item.attributes).every(a=>a.type===''&&!a.isPrimaryKey)).toBe(true);
    const stored=JSON.parse(localStorage.getItem('uml-projects-v1')!)[0].model;
    component.model.set((component as any).normalizeModel(stored));
    expect(component.classes().flatMap(item=>item.attributes)).toHaveLength(8);
    const paciente=component.classes()[0]; component.selectClass(paciente);
    expect(component.attributeText(paciente.attributes[0])).toContain('Sin definir');
    component.editAttribute(paciente.attributes[0]); component.attributeDraft.type='Long'; component.saveAttribute();
    expect(component.classes()[0].attributes[0].type).toBe('Long');
    expect(component.relations()[0]).toEqual(expect.objectContaining({label:'reserva',sourceMultiplicity:'1',targetMultiplicity:'0..*'}));
  });

  it('actualiza y elimina mediante IA usando IDs internos', () => {
    component.newClassName = 'Cliente'; component.addClass();
    component.newClassName = 'Venta'; component.addClass();
    const [cliente, venta] = component.classes();
    (component as any).executeAiCommand({ status: 'READY', operation: 'RELATION_CREATE', parameters: { sourceId: cliente.id, targetId: venta.id, sourceMultiplicity: '1', targetMultiplicity: '0..*', type: 'association' }, message: 'ok' }, () => {});
    const relation = component.relations()[0];
    (component as any).executeAiCommand({ status: 'READY', operation: 'RELATION_UPDATE', targetId: relation.id, parameters: { targetMultiplicity: '1..*' }, message: 'ok' }, () => {});
    expect(component.relations()[0].targetMultiplicity).toBe('1..*');
    (component as any).executeAiCommand({ status: 'READY', operation: 'RELATION_DELETE', targetId: relation.id, parameters: {}, message: 'ok' }, () => {});
    expect(component.relations()).toHaveLength(0);
    expect(component.classes()).toHaveLength(2);
  });

  it('ejecuta operaciones IA compuestas en orden conservando los IDs resueltos', () => {
    component.newClassName = 'Pedido'; component.addClass(); const pedido = component.classes()[0];
    const commands: any[] = [
      { status: 'READY', operation: 'CLASS_CREATE', targetId: 'class-ai-cliente', parameters: { id: 'class-ai-cliente', name: 'Cliente', attributes: [{ id: 'attr-ai-id', name: 'id', type: 'Long', isPrimaryKey: true }] }, message: 'ok' },
      { status: 'READY', operation: 'RELATION_CREATE', targetId: 'rel-ai-1', parameters: { id: 'rel-ai-1', sourceId: 'class-ai-cliente', targetId: pedido.id, type: 'association', sourceMultiplicity: '1', targetMultiplicity: '0..*' }, message: 'ok' }
    ];
    (component as any).executeAiCommands(commands, 0, () => {});
    expect(component.classes().find(item => item.id === 'class-ai-cliente')?.attributes[0]).toEqual(expect.objectContaining({ isPrimaryKey: true }));
    expect(component.relations()[0]).toEqual(expect.objectContaining({ id: 'rel-ai-1', sourceId: 'class-ai-cliente', targetId: pedido.id }));
  });

  it('envía automáticamente una transcripción final una sola vez por el mismo flujo de texto', async () => {
    const interpret = vi.spyOn((component as any).umlAi, 'interpret').mockReturnValue(of({
      status: 'READY', interpreter: 'GEMINI', fallbackUsed: false, message: 'Mascota creada.',
      operations: [{ type: 'CLASS_CREATE', targetId: 'mascota-ai', elementType: 'class', target: { className: 'Mascota' }, parameters: { id: 'mascota-ai', name: 'Mascota', attributes: [] } }]
    }));
    (component as any).voiceCaptureActive = true;

    component.voice.finalResult.set({ id: 101, text: 'Crea una clase Mascota' });
    await new Promise(resolve => window.setTimeout(resolve, 10));
    component.voice.finalResult.set({ id: 101, text: 'Crea una clase Mascota' });
    await new Promise(resolve => window.setTimeout(resolve, 10));

    expect(interpret).toHaveBeenCalledTimes(1);
    expect(interpret).toHaveBeenCalledWith('Crea una clase Mascota', expect.any(Object));
    expect(component.classes().find(item => item.name === 'Mascota')).toBeTruthy();
  });
});
