import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { roleGuard } from './auth/role.guard';

export const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'proyectos', pathMatch: 'full' },
      {
        path: 'usuarios',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () => import('./pages/admin-usuarios/admin-usuarios.component').then(m => m.AdminUsuariosComponent)
      },
      {
        path: 'proyectos',
        loadComponent: () => import('./pages/uml-projects/uml-projects.component').then(m => m.UmlProjectsComponent)
      },
      {
        path: 'diagrama-clases/:projectId',
        loadComponent: () => import('./pages/uml-class-diagram/uml-class-diagram.component').then(m => m.UmlClassDiagramComponent)
      },
      { path: 'diagrama-clases', redirectTo: 'proyectos', pathMatch: 'full' },
      { path: 'diagrama-uml', redirectTo: 'proyectos', pathMatch: 'full' }
    ]
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'registro',
    loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent)
  },
  { path: '**', redirectTo: '' }
];
