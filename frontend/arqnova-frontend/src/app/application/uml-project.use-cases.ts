import {
  Observable,
  catchError,
  concatMap,
  forkJoin,
  from,
  map,
  of,
  switchMap,
  tap,
  throwError,
  toArray,
} from 'rxjs';
import {
  DEFAULT_UML_MODEL,
  UmlDiagramModel,
  UmlProject,
  UmlProjectStatus,
} from '../domain/uml/uml-class-diagram.models';
import { ProjectDto } from '../domain/uml/project-records';
import { ProjectRepositoryPort } from './ports/project-repository.port';
import { KeyValueStore } from './ports/platform.ports';
export class UmlProjectUseCases {
  private static readonly STORAGE_KEY = 'uml-projects-v1';
  private static readonly LEGACY_MODEL_KEY = 'uml-conceptual-model-v1';
  private static readonly MIGRATION_KEY = 'uml-projects-postgres-migration-v1';
  constructor(
    private readonly repository: ProjectRepositoryPort,
    private readonly storage: KeyValueStore,
  ) {}

  list(): Observable<UmlProject[]> {
    return this.repository.list().pipe(
      map((response) => response.datos.map((project) => this.fromDto(project))),
      tap((projects) => this.markRemoteProjects(projects)),
      switchMap((remote) =>
        this.migratePending(remote).pipe(
          switchMap((results) =>
            results.length
              ? this.repository.list().pipe(
                  map((response) => response.datos.map((project) => this.fromDto(project))),
                  tap((projects) => this.markRemoteProjects(projects)),
                )
              : of(remote),
          ),
        ),
      ),
      map((projects) => {
        const ids = new Set(projects.map((project) => project.id));
        const pendingLocal = this.read().filter(
          (project) => !ids.has(project.id) && this.isPendingLocal(project),
        );
        return [...projects, ...pendingLocal];
      }),
      tap((projects) => this.mergeCache(projects)),
    );
  }

  get(id: string): Observable<UmlProject> {
    return forkJoin({
      project: this.repository.project(id),
      diagram: this.repository.diagram(id),
    }).pipe(
      map(({ project, diagram }) => {
        const value = this.fromDto(project.datos, diagram.datos.model, diagram.datos.modelVersion);
        this.putCache(value);
        return value;
      }),
    );
  }

  create(
    name: string,
    description: string,
    status: UmlProjectStatus,
    id = this.id(),
  ): Observable<UmlProject> {
    const now = new Date().toISOString();
    const model = this.clone(DEFAULT_UML_MODEL);
    model.title = name.trim();
    model.updatedAt = now;
    return this.repository
      .create({ id, name: name.trim(), description: description.trim(), status })
      .pipe(
        map((response) => this.fromDto(response.datos, model, 0)),
        tap((project) => {
          const migrated = this.migrationMarks();
          migrated[project.id] = new Date().toISOString();
          this.writeMigrationMarks(migrated);
          this.putCache(project);
        }),
      );
  }

  updateMetadata(
    id: string,
    changes: { name: string; description: string; status: UmlProjectStatus },
  ): Observable<UmlProject> {
    const payload = {
      ...changes,
      name: changes.name.trim(),
      description: changes.description.trim(),
    };
    return this.repository.update(id, payload).pipe(
      map((response) => {
        const cached = this.cached(id);
        return this.fromDto(response.datos, cached?.model, cached?.modelVersion ?? 0);
      }),
      tap((project) => this.putCache(project)),
    );
  }

  saveModel(id: string, model: UmlDiagramModel): Observable<UmlProject> {
    const cached = this.cached(id);
    if (!cached)
      return throwError(() => new Error('El proyecto no está disponible en la caché local.'));
    this.putCache({
      ...cached,
      name: model.title || cached.name,
      model: this.clone(model),
      updatedAt: model.updatedAt || new Date().toISOString(),
    });
    return this.saveDiagram(
      { ...cached, owner: cached.owner ?? '', memberRole: cached.memberRole ?? 'OWNER' },
      model,
      cached.modelVersion ?? 0,
    ).pipe(tap((project) => this.putCache(project)));
  }

  upsertCollaborative(id: string, name: string, model: UmlDiagramModel): UmlProject {
    const existing = this.cached(id);
    const now = new Date().toISOString();
    const project: UmlProject = existing
      ? {
          ...existing,
          name: model.title || existing.name,
          model: this.clone(model),
          updatedAt: now,
        }
      : {
          id,
          name,
          description: 'Proyecto UML compartido mediante una sesión colaborativa.',
          status: 'EN_EDICION',
          createdAt: now,
          updatedAt: now,
          model: this.clone(model),
          modelVersion: 0,
        };
    this.putCache(project);
    return project;
  }

  delete(id: string): Observable<void> {
    return this.repository
      .delete(id)
      .pipe(tap(() => this.write(this.read().filter((project) => project.id !== id))));
  }

  cachedList(): UmlProject[] {
    return this.read().sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }
  cached(id: string): UmlProject | null {
    return this.read().find((project) => project.id === id) ?? null;
  }
  cacheModel(id: string, model: UmlDiagramModel) {
    const project = this.cached(id);
    if (project)
      this.putCache({
        ...project,
        name: model.title || project.name,
        updatedAt: model.updatedAt || new Date().toISOString(),
        model: this.clone(model),
      });
  }

  migrateLegacyModel(): UmlProject | null {
    if (this.read().length) return null;
    try {
      const raw = this.storage.getItem(UmlProjectUseCases.LEGACY_MODEL_KEY);
      if (!raw) return null;
      const model = JSON.parse(raw) as UmlDiagramModel;
      if (
        model?.format !== 'uml-class-diagram' ||
        !Array.isArray(model.classes) ||
        !Array.isArray(model.relations)
      )
        return null;
      const now = model.updatedAt || new Date().toISOString();
      const project: UmlProject = {
        id: this.id(),
        name: model.title || 'Proyecto UML migrado',
        description: 'Diagrama recuperado de la versión anterior.',
        status: 'EN_EDICION',
        createdAt: now,
        updatedAt: now,
        model,
        modelVersion: 0,
      };
      this.write([project]);
      return project;
    } catch {
      return null;
    }
  }

  private migratePending(remote: UmlProject[]): Observable<boolean[]> {
    const remoteIds = new Set(remote.map((project) => project.id));
    const migrated = this.migrationMarks();
    const pending = this.read().filter(
      (project) =>
        !migrated[project.id] && !remoteIds.has(project.id) && this.isPendingLocal(project),
    );
    if (!pending.length) return of([]);
    return from(pending).pipe(
      concatMap((project) =>
        this.repository
          .create({
            id: project.id,
            name: project.name,
            description: project.description,
            status: project.status,
          })
          .pipe(
            switchMap((response) => this.saveDiagram(response.datos, project.model, 0)),
            tap(() => {
              migrated[project.id] = new Date().toISOString();
              this.writeMigrationMarks(migrated);
            }),
            map(() => true),
            catchError(() => of(false)),
          ),
      ),
      toArray(),
    );
  }

  private saveDiagram(
    project: ProjectDto | UmlProject,
    model: UmlDiagramModel,
    version: number,
  ): Observable<UmlProject> {
    return this.repository.saveDiagram(project.id, this.clone(model), version).pipe(
      map((response) => ({
        id: project.id,
        name: model.title || project.name,
        description: project.description,
        status: project.status,
        owner: project.owner,
        memberRole: project.memberRole,
        createdAt: project.createdAt,
        updatedAt: model.updatedAt || new Date().toISOString(),
        model: this.clone(response.datos.model),
        modelVersion: response.datos.modelVersion,
      })),
    );
  }

  private fromDto(dto: ProjectDto, model?: UmlDiagramModel, modelVersion = 0): UmlProject {
    const value = model ? this.clone(model) : this.clone(DEFAULT_UML_MODEL);
    value.title = dto.name;
    return { ...dto, model: value, modelVersion };
  }
  private mergeCache(projects: UmlProject[]) {
    const local = this.read();
    const merged = projects.map((project) => {
      const cached = local.find((item) => item.id === project.id);
      return cached
        ? { ...project, model: cached.model, modelVersion: cached.modelVersion }
        : project;
    });
    this.write(merged);
  }
  private markRemoteProjects(projects: UmlProject[]) {
    const marks = this.migrationMarks();
    let changed = false;
    for (const project of projects) {
      if (!marks[project.id]) {
        marks[project.id] = new Date().toISOString();
        changed = true;
      }
    }
    if (changed) this.writeMigrationMarks(marks);
  }
  private isPendingLocal(project: UmlProject) {
    return !this.migrationMarks()[project.id] && !project.owner && !project.memberRole;
  }
  private putCache(project: UmlProject) {
    this.write([...this.read().filter((item) => item.id !== project.id), this.clone(project)]);
  }
  private read(): UmlProject[] {
    try {
      const parsed = JSON.parse(this.storage.getItem(UmlProjectUseCases.STORAGE_KEY) ?? '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  private write(projects: UmlProject[]) {
    this.storage.setItem(UmlProjectUseCases.STORAGE_KEY, JSON.stringify(projects));
  }
  private migrationMarks(): Record<string, string> {
    try {
      return JSON.parse(this.storage.getItem(UmlProjectUseCases.MIGRATION_KEY) ?? '{}') || {};
    } catch {
      return {};
    }
  }
  private writeMigrationMarks(value: Record<string, string>) {
    this.storage.setItem(UmlProjectUseCases.MIGRATION_KEY, JSON.stringify(value));
  }
  private clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value));
  }
  private id() {
    return `uml-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }
}
