import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Icon } from '../shared/ui/icon';
import { TAB_ITEMS } from './nav-items';

/** Barra de abas inferior (celular e tablet). */
@Component({
  selector: 'ww-tab-bar',
  imports: [RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav aria-label="Navegação principal">
      @for (item of items; track item.path) {
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
    }
    a.active {
      color: var(--ww-pink-strong);
      font-weight: 600;
    }
  `,
})
export class TabBar {
  protected readonly items = TAB_ITEMS;
}
