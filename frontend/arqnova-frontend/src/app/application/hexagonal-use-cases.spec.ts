import { describe, it, expect } from 'vitest';
import { of } from 'rxjs';
import { AuthUseCases } from './auth.use-cases';
import { UmlProjectUseCases } from './uml-project.use-cases';
import { IdentityPort } from './ports/identity.port';
import { KeyValueStore, StateFactory, StateCell } from './ports/platform.ports';
import { ProjectRepositoryPort } from './ports/project-repository.port';
import { DEFAULT_UML_MODEL } from '../domain/uml/uml-class-diagram.models';
import { buildImageImport, createAssociationClass } from '../domain/uml/diagram-rules';
import { UmlImageAnalysis } from '../domain/uml/ai.models';
class MemoryStorage implements KeyValueStore {
  values = new Map<string, string>();
  getItem(k: string) {
    return this.values.get(k) ?? null;
  }
  setItem(k: string, v: string) {
    this.values.set(k, v);
  }
  removeItem(k: string) {
    this.values.delete(k);
  }
}
const state: StateFactory = {
  cell<T>(initial: T): StateCell<T> {
    let value = initial;
    const cell = (() => value) as StateCell<T>;
    cell.set = (next) => {
      value = next;
    };
    cell.update = (fn) => {
      value = fn(value);
    };
    return cell;
  },
};
describe('casos de uso sin Angular ni navegador', () => {
  it('autentica y conserva la sesión usando puertos de memoria', async () => {
    const storage = new MemoryStorage();
    let request: any;
    const api: IdentityPort = {
      login: (value) => {
        request = value;
        return of({
          exito: true,
          datos: {
            username: value.username,
            rol: 'ADMINISTRADOR',
            departamento: 'Sistemas',
            token: 'token',
          },
        });
      },
      register: () => of({ exito: true }),
      currentSession: () => of({ datos: {} }),
      logout: () => of(void 0),
    };
    const service = new AuthUseCases(api, storage, state);
    service.login({ username: '  ana  ', password: 'clave' }).subscribe();
    expect(request.username).toBe('ana');
    expect(service.isAdministrador()).toBe(true);
    expect(JSON.parse(storage.getItem('ARQNOVA_USER')!).username).toBe('ana');
    service.logout().subscribe();
    expect(service.currentUser()).toBeNull();
    expect(storage.getItem('ARQNOVA_USER')).toBeNull();
  });
  it('guarda el modelo y conserva la versión a través del contrato del repositorio', () => {
    const storage = new MemoryStorage();
    let written: any;
    const project: any = {
      id: 'p',
      name: 'Curso',
      description: '',
      status: 'EN_EDICION',
      owner: 'ana',
      memberRole: 'OWNER',
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    };
    const repository: ProjectRepositoryPort = {
      list: () => of({ datos: [] }),
      project: () => of({ datos: project }),
      diagram: () => of({ datos: { projectId: 'p', modelVersion: 0, model: DEFAULT_UML_MODEL } }),
      create: () => of({ datos: project }),
      update: () => of({ datos: project }),
      delete: () => of(void 0),
      saveDiagram: (id, model, version) => {
        written = { id, model, version };
        return of({ datos: { projectId: id, modelVersion: version + 1, model } });
      },
    };
    const service = new UmlProjectUseCases(repository, storage);
    service.create('Curso', '', 'EN_EDICION', 'p').subscribe();
    service.saveModel('p', structuredClone(DEFAULT_UML_MODEL)).subscribe();
    expect(written.version).toBe(0);
    expect(service.cached('p')?.modelVersion).toBe(1);
  });
  it('importa clases de asociación con nombres arbitrarios sin depender del componente visual', () => {
    const analysis: UmlImageAnalysis = {
      status: 'READY',
      provider: 'GEMINI',
      model: 'fixture',
      message: 'ok',
      warnings: [],
      classes: [
        { name: 'Curso', attributes: [] },
        { name: 'Alumno', attributes: [] },
        { name: 'Inscripcion', attributes: [{ name: 'nota', type: '', isPrimaryKey: false }] },
      ],
      relations: [
        {
          sourceClassName: 'Curso',
          targetClassName: 'Alumno',
          type: 'association',
          label: '',
          sourceMultiplicity: '*',
          targetMultiplicity: '1..*',
          associationClassName: 'Inscripcion',
        },
      ],
    };
    let id = 0;
    const result = buildImageImport(
      structuredClone(DEFAULT_UML_MODEL),
      analysis,
      true,
      { cardWidth: 280, canvasWidth: 1800, canvasHeight: 1200 },
      (prefix) => `${prefix}-${++id}`,
    );
    expect(result.model!.classes).toHaveLength(3);
    expect(result.model!.relations).toHaveLength(1);
    const association = result.model!.classes.find((c) => c.name === 'Inscripcion')!;
    expect(result.model!.relations[0].associationClassId).toBe(association.id);
    expect(association.attributes[0].name).toBe('nota');
  });
  it('mantiene la creación automática manual para muchos a muchos y evita crearla para uno a muchos', () => {
    const node = (id: string) => ({
      id,
      name: id,
      attributes: [],
      methods: [],
      responsibilities: [],
      x: 0,
      y: 0,
      color: '#2563eb',
    });
    const relation: any = {
      id: 'r',
      sourceId: 'A',
      targetId: 'B',
      type: 'association',
      label: '',
      sourceMultiplicity: '*',
      targetMultiplicity: '*',
    };
    const layout = { cardWidth: 280, canvasWidth: 1800, canvasHeight: 1200 };
    expect(
      createAssociationClass(
        relation,
        [node('A'), node('B')],
        layout,
        () => 'association',
        'Participacion',
      )?.name,
    ).toBe('Participacion');
    expect(
      createAssociationClass(
        { ...relation, associationClassId: undefined, sourceMultiplicity: '1' },
        [node('A'), node('B')],
        layout,
        () => 'another',
      ),
    ).toBeNull();
  });
});
