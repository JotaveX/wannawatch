import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AuthStore } from '../core/auth/auth-store';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Icon } from '../shared/ui/icon';
import { GUEST_TAB, TAB_ITEMS } from './nav-items';

/** Barra de abas inferior (celular e tablet). Sem login, a aba de Perfil vira "Entrar". */
@Component({
  selector: 'ww-tab-bar',
  imports: [RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav aria-label="Navegação principal">
      @for (item of items(); track item.path) {
        <a
          [routerLink]="item.path"
          routerLinkActive="active"
          [routerLinkActiveOptions]="{ exact: !!item.exact }"
          ariaCurrentWhenActive="page"
        >
          <ww-icon [name]="item.icon" />
          <span>{{ item.label }}</span>
        </a>
      }
    </nav>
  `,
  styles: `
    nav {
      display: grid;
      grid-template-columns: repeat(5, minmax(0, 1fr));
      height: var(--ww-tabbar-h);
      padding: 6px 6px env(safe-area-inset-bottom);
      box-sizing: content-box;
      background: var(--ww-white);
      border-top: var(--ww-hairline) solid var(--ww-border);
    }
    a {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      min-height: var(--ww-touch);
      color: var(--ww-gray-text);
      font-size: 11px;
      font-weight: 500;
      text-decoration: none;
      -webkit-tap-highlight-color: transparent;
      transition: color var(--ww-medium) var(--ww-ease-out);
    }
    /* pílula atrás do ícone ativo: cresce do centro */
    a::before {
      content: '';
      position: absolute;
      top: 2px;
      left: 50%;
      width: 56px;
      height: 30px;
      margin-left: -28px;
      border-radius: var(--ww-radius-pill);
      background: rgba(215, 15, 69, 0.1);
      opacity: 0;
      transform: scale(0.5);
      transition:
        opacity var(--ww-medium) var(--ww-ease-out),
        transform var(--ww-slow) var(--ww-ease-spring);
    }
    ww-icon {
      position: relative;
      margin-top: 1px;
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    a:active ww-icon {
      transform: scale(0.88);
    }
    a.active {
      color: var(--ww-pink-strong);
      font-weight: 600;
    }
    a.active::before {
      opacity: 1;
      transform: scale(1);
    }
  `,
})
export class TabBar {
  private readonly auth = inject(AuthStore);
  protected readonly items = computed(() =>
    this.auth.loggedIn()
      ? TAB_ITEMS
      : TAB_ITEMS.map((item) => (item.path === '/perfil' ? GUEST_TAB : item)),
  );
}
