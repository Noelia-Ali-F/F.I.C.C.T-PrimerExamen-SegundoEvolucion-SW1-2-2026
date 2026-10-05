import { CommonModule } from '@angular/common';
import { Component, OnDestroy, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { UmlProject, UmlProjectStatus } from '../uml-class-diagram/uml-class-diagram.models';
import { UmlProjectStoreService } from '../uml-class-diagram/uml-project-store.service';

type ProjectDraft = { name: string; description: string; status: UmlProjectStatus };

@Component({
  selector: 'app-uml-projects', standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  templateUrl: './uml-projects.component.html',
  styleUrls: ['./uml-projects.component.scss', './uml-projects.actions.scss']
})
export class UmlProjectsComponent implements OnDestroy {
  private readonly refreshInterval: number;
  projects = signal<UmlProject[]>([]);
  search = signal('');
  statusFilter = signal<'TODOS' | UmlProjectStatus>('TODOS');
  dateFilter = signal('');
  modalOpen = signal(false);
  editingId = signal<string | null>(null);
  deleteTarget = signal<UmlProject | null>(null);
  deleteError = signal('');
  error = signal('');
  loading = signal(true);
  saving = signal(false);
  isCreating = signal(false);
  draft: ProjectDraft = this.emptyDraft();

  filteredProjects = computed(() => {
    const query = this.search().trim().toLocaleLowerCase();
    return this.projects().filter(project => {
      const matchesText = !query || `${project.name} ${project.description} ${project.owner ?? ''}`.toLocaleLowerCase().includes(query);
      const matchesStatus = this.statusFilter() === 'TODOS' || project.status === this.statusFilter();
      const matchesDate = !this.dateFilter() || project.updatedAt?.slice(0, 10) === this.dateFilter();
      return matchesText && matchesStatus && matchesDate;
    }).sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''));
  });

  constructor(private readonly store: UmlProjectStoreService, private readonly router: Router) {
    this.store.migrateLegacyModel();
    this.refresh();
    this.refreshInterval=window.setInterval(()=>this.refresh(false),5000);
  }

  ngOnDestroy() { window.clearInterval(this.refreshInterval); }

  openCreate() { this.editingId.set(null); this.draft = this.emptyDraft(); this.error.set(''); this.modalOpen.set(true); }
  openEdit(project: UmlProject) { this.editingId.set(project.id); this.draft = { name: project.name, description: project.description, status: project.status }; this.error.set(''); this.modalOpen.set(true); }
  closeModal() { this.modalOpen.set(false); this.error.set(''); }

  submitProject() {
    const id = this.editingId();
    if (this.saving() || (!id && this.isCreating())) return;
    if (!this.draft.name.trim()) { this.error.set('El nombre del proyecto es obligatorio.'); return; }
    if (this.draft.name.trim().length > 150) { this.error.set('El nombre no puede superar los 150 caracteres.'); return; }
    if (this.draft.description.trim().length > 4000) { this.error.set('La descripción no puede superar los 4000 caracteres.'); return; }
    const normalizedName = this.draft.name.trim().toLocaleLowerCase();
    const duplicate = this.projects().some(project => project.id !== id && project.name.trim().toLocaleLowerCase() === normalizedName);
    if (duplicate) { this.error.set('Ya existe un proyecto UML con ese nombre.'); return; }
    this.draft = { ...this.draft, name: this.draft.name.trim(), description: this.draft.description.trim() };
    this.saving.set(true);
    if (!id) this.isCreating.set(true);
    const request = id ? this.store.updateMetadata(id, this.draft) : this.store.create(this.draft.name, this.draft.description, this.draft.status);
    request.subscribe({
      next: project => {
        this.saving.set(false);
        this.isCreating.set(false);
        this.closeModal();
        if (id) this.refresh();
        else {
          this.projects.update(projects => [project, ...projects.filter(item => item.id !== project.id)]);
          this.openProject(project);
        }
      },
      error: error => {
        this.saving.set(false);
        this.isCreating.set(false);
        this.error.set(error?.error?.mensaje || (id
          ? 'No se pudo actualizar el proyecto. Inténtalo nuevamente.'
          : 'No se pudo crear el proyecto. Inténtalo nuevamente.'));
      }
    });
  }

  openProject(project: UmlProject) { this.router.navigate(['/diagrama-clases', project.id]); }
  startCollaboration(project: UmlProject) {
    this.router.navigate(['/diagrama-clases', project.id], { queryParams: { collaborate: '1' } });
  }
  requestDelete(project: UmlProject) { this.deleteError.set(''); this.deleteTarget.set(project); }
  cancelDelete() { if (this.saving()) return; this.deleteTarget.set(null); this.deleteError.set(''); }
  confirmDelete() { const project=this.deleteTarget();if(!project||this.saving())return;this.saving.set(true);this.deleteError.set('');this.store.delete(project.id).subscribe({next:()=>{this.saving.set(false);this.deleteTarget.set(null);this.refresh();},error:error=>{this.saving.set(false);this.deleteError.set(error?.error?.mensaje||'No se pudo eliminar el proyecto. Inténtalo nuevamente.');}}); }
  statusLabel(status: UmlProjectStatus) { return ({ EN_EDICION: 'En edición', COMPLETADO: 'Completado', PENDIENTE: 'Pendiente' })[status]; }
  ownerLabel(project: UmlProject) { return project.owner?.trim() || 'Proyecto local'; }
  formatDate(value: string) { const date=new Date(value);return Number.isNaN(date.getTime())?'No disponible':new Intl.DateTimeFormat('es-BO', { dateStyle: 'short', timeStyle: 'short' }).format(date); }
  trackProject(_: number, project: UmlProject) { return project.id; }
  private refresh(showLoading=true) { if(showLoading)this.loading.set(true);this.store.list().subscribe({next:projects=>{this.projects.set(projects);this.loading.set(false);this.error.set('');},error:()=>{if(showLoading)this.projects.set(this.store.cachedList());this.loading.set(false);this.error.set('No se pudo consultar PostgreSQL. Se muestra la copia local sin sobrescribir datos remotos.');}}); }
  private emptyDraft(): ProjectDraft { return { name: '', description: '', status: 'EN_EDICION' }; }
}
