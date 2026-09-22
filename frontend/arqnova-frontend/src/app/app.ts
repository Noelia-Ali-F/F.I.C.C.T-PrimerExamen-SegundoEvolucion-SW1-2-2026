import { Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from './auth/auth.service';
import { BASE_PATH } from './api/variables';

type SidebarRoute = {
  label: string;
  route: string;
  icon: string;
  roles?: Array<'SOLICITANTE' | 'REVISOR' | 'ADMINISTRADOR'>;
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatIconModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnDestroy {
  private static readonly SIDEBAR_COMPACT_STORAGE_KEY = 'UML_SIDEBAR_COMPACT';
  private static readonly NAV_ITEMS: SidebarRoute[] = [
    { label: 'Proyectos UML', route: '/proyectos', icon: 'folder_open' }
  ];

  readonly authService = inject(AuthService);
  readonly router = inject(Router);
  readonly basePath = inject(BASE_PATH);
  readonly cacheBuster = Date.now();

  private readonly currentUrl = signal('/');
  private readonly isDesktopViewport = signal(false);
  readonly isDesktopLayout = this.isDesktopViewport.asReadonly();
  readonly sidebarOpen = signal(true);
  readonly sidebarCompact = signal(false);
  readonly profileMenuOpen = signal(false);

  readonly navItems = computed(() => {
    const role = this.authService.currentUser()?.rol;
    return App.NAV_ITEMS.filter(item => !item.roles || (!!role && item.roles.includes(role)));
  });

  readonly isStandaloneRoute = computed(() => this.currentUrl().startsWith('/diagrama-clases/'));
  readonly currentRouteTitle = computed(() => {
    const url = this.currentUrl();
    if (url.startsWith('/usuarios')) return 'Administración de usuarios';
    if (url.startsWith('/diagrama-clases/')) return 'Editor UML';
    return 'Proyectos de modelado UML';
  });

  private readonly resizeHandler = () => this.syncViewportMode();

  constructor() {
    this.currentUrl.set(this.normalizeUrl(this.router.url));
    if (typeof window !== 'undefined') {
      this.sidebarCompact.set(window.localStorage.getItem(App.SIDEBAR_COMPACT_STORAGE_KEY) === '1');
      window.addEventListener('resize', this.resizeHandler);
    }
    this.syncViewportMode();
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe(event => {
      this.currentUrl.set(this.normalizeUrl(event.urlAfterRedirects));
      if (!this.isDesktopViewport()) this.sidebarOpen.set(false);
      this.profileMenuOpen.set(false);
    });
  }

  toggleSidebar() { this.sidebarOpen.update(value => !value); }
  closeSidebar() { if (!this.isDesktopViewport()) this.sidebarOpen.set(false); }
  toggleProfileMenu() { this.profileMenuOpen.update(value => !value); }

  toggleSidebarCompact() {
    this.sidebarCompact.update(value => !value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(App.SIDEBAR_COMPACT_STORAGE_KEY, this.sidebarCompact() ? '1' : '0');
    }
  }

  isRouteActive(path: string): boolean {
    return this.currentUrl().startsWith(path);
  }

  visibleRole(role: 'SOLICITANTE' | 'REVISOR' | 'ADMINISTRADOR'): 'Administrador' | 'Usuario/Colaborador' {
    return role === 'ADMINISTRADOR' ? 'Administrador' : 'Usuario/Colaborador';
  }

  logout() {
    this.authService.logout().subscribe({
      complete: () => this.router.navigate(['/login'], { replaceUrl: true }),
      error: () => {
        this.authService.clearSession();
        this.router.navigate(['/login'], { replaceUrl: true });
      }
    });
  }

  ngOnDestroy() {
    if (typeof window !== 'undefined') window.removeEventListener('resize', this.resizeHandler);
  }

  private syncViewportMode() {
    if (typeof window === 'undefined') return;
    const desktop = window.matchMedia('(min-width: 1024px)').matches;
    this.isDesktopViewport.set(desktop);
    if (desktop) this.sidebarOpen.set(true);
  }

  private normalizeUrl(url: string): string {
    return url.split('?')[0].split('#')[0].trim() || '/';
  }
}
