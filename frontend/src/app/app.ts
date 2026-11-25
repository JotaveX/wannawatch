import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { DEFAULT_META, RouteMeta } from './layout/route-meta';
import { SideNav } from './layout/side-nav';
import { TabBar } from './layout/tab-bar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, SideNav, TabBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  protected readonly meta = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => routeMeta(this.router.routerState.snapshot.root)),
    ),
    { initialValue: DEFAULT_META },
  );

  protected readonly feed = computed(() => this.meta().shell === 'feed');
  protected readonly bare = computed(() => this.meta().shell === 'bare');

  /**
   * Com <base href="/">, "#conteudo" apontaria para "/#conteudo" (recarregaria a Home),
   * então o link só move o foco para o <main>.
   */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.document.getElementById('conteudo')?.focus();
  }

  constructor() {
    // O tema vai no <html> para o fundo da página inteira acompanhar.
    effect(() => {
      this.document.documentElement.dataset['theme'] = this.meta().theme;
    });
  }
}

/** Junta o `data` da rota mais profunda com os padrões. */
function routeMeta(root: ActivatedRouteSnapshot): RouteMeta {
  let route = root;
  while (route.firstChild) route = route.firstChild;
  return { ...DEFAULT_META, ...(route.data as Partial<RouteMeta>) };
}
