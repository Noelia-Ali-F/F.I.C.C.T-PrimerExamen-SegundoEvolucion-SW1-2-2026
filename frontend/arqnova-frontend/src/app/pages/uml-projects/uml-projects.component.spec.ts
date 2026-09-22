import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { UmlProject } from '../uml-class-diagram/uml-class-diagram.models';
import { UmlProjectStoreService } from '../uml-class-diagram/uml-project-store.service';
import { UmlProjectsComponent } from './uml-projects.component';

describe('UmlProjectsComponent', () => {
  let component: UmlProjectsComponent;
  let createResult: Subject<UmlProject>;
  let store: {
    migrateLegacyModel: ReturnType<typeof vi.fn>;
    list: ReturnType<typeof vi.fn>;
    cachedList: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    updateMetadata: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    createResult = new Subject<UmlProject>();
    store = {
      migrateLegacyModel: vi.fn(),
      list: vi.fn(() => of([])),
      cachedList: vi.fn(() => []),
      create: vi.fn(() => createResult.asObservable()),
      updateMetadata: vi.fn((id: string, changes: { name: string; description: string; status: 'EN_EDICION' }) =>
        of({ ...project(id), ...changes })),
      delete: vi.fn(() => of(void 0))
    };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [UmlProjectsComponent],
      providers: [
        { provide: UmlProjectStoreService, useValue: store },
        { provide: Router, useValue: router }
      ]
    });
    component = TestBed.createComponent(UmlProjectsComponent).componentInstance;
    component.openCreate();
    component.draft.name = 'PruebaUnica';
  });

  it('un envío crea exactamente una vez y navega usando el ID remoto', () => {
    component.submitProject();
    expect(store.create).toHaveBeenCalledTimes(1);
    createResult.next(project('remote-id'));
    createResult.complete();
    expect(router.navigate).toHaveBeenCalledWith(['/diagrama-clases', 'remote-id']);
    expect(component.modalOpen()).toBe(false);
  });

  it('ignora un segundo envío mientras la creación continúa', () => {
    component.submitProject();
    component.submitProject();
    expect(store.create).toHaveBeenCalledTimes(1);
    expect(component.isCreating()).toBe(true);
  });

  it('rechaza nombres vacíos, demasiado largos y duplicados antes de llamar al backend', () => {
    component.draft.name = '   ';
    component.submitProject();
    expect(component.error()).toBe('El nombre del proyecto es obligatorio.');

    component.draft.name = 'x'.repeat(151);
    component.submitProject();
    expect(component.error()).toBe('El nombre no puede superar los 150 caracteres.');

    component.projects.set([{ ...project('existing'), name: 'Biblioteca UML' }]);
    component.draft.name = '  biblioteca uml  ';
    component.submitProject();
    expect(component.error()).toBe('Ya existe un proyecto UML con ese nombre.');
    expect(store.create).not.toHaveBeenCalled();
  });

  it('abre el editor y la colaboración usando la ruta existente del proyecto', () => {
    const value = project('project-42');
    component.openProject(value);
    component.startCollaboration(value);
    expect(router.navigate).toHaveBeenNthCalledWith(1, ['/diagrama-clases', 'project-42']);
    expect(router.navigate).toHaveBeenNthCalledWith(2, ['/diagrama-clases', 'project-42'], { queryParams: { collaborate: '1' } });
  });

  it('actualiza metadatos recortados sin alterar el identificador', () => {
    const value = project('project-edit');
    component.openEdit(value);
    component.draft.name = '  Proyecto actualizado  ';
    component.draft.description = '  Descripción  ';
    component.submitProject();
    expect(store.updateMetadata).toHaveBeenCalledWith('project-edit', {
      name: 'Proyecto actualizado', description: 'Descripción', status: 'EN_EDICION'
    });
  });

  it('solo elimina después de confirmar y conserva el proyecto si se cancela', () => {
    const value = project('project-delete');
    component.requestDelete(value);
    component.cancelDelete();
    expect(store.delete).not.toHaveBeenCalled();

    component.requestDelete(value);
    component.confirmDelete();
    expect(store.delete).toHaveBeenCalledWith('project-delete');
    expect(component.deleteTarget()).toBeNull();
  });

  it('ordena por última modificación y expone creador y fechas del proyecto', () => {
    const older = { ...project('older'), owner: 'ana', updatedAt: '2026-01-01T10:00:00Z' };
    const newer = { ...project('newer'), owner: 'juan', updatedAt: '2026-02-01T10:00:00Z' };
    component.projects.set([older, newer]);
    expect(component.filteredProjects().map(item => item.id)).toEqual(['newer', 'older']);
    expect(component.ownerLabel(newer)).toBe('juan');
    expect(component.formatDate(newer.createdAt)).not.toBe('No disponible');
  });

  function project(id: string): UmlProject {
    const now = new Date().toISOString();
    return { id, name: 'PruebaUnica', description: '', status: 'EN_EDICION', createdAt: now, updatedAt: now,
      model: { format: 'uml-class-diagram', version: 2, title: 'PruebaUnica', classes: [], relations: [] }, modelVersion: 0 };
  }
});
