import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthStore } from '../core/auth/auth-store';
import { Avatar } from '../shared/ui/avatar';
import { Icon } from '../shared/ui/icon';
import { Logo } from '../shared/ui/logo';
import { ActiveSlider } from './active-slider';
import { SIDE_ITEMS } from './nav-items';

/** Barra lateral do desktop, no Swipe, no Reels e nas telas secundárias (estilo TikTok web). */
@Component({
  selector: 'ww-side-nav',
  imports: [RouterLink, RouterLinkActive, Avatar, Icon, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'complementary',
    'aria-label': 'Menu do wannawatch?',
    '(window:resize)': 'slider.measure()',
  },
  template: `
    <a routerLink="/" class="brand" aria-label="wannawatch? — início"><ww-logo [size]="26" [mark]="true" /></a>

    <nav aria-label="Navegação principal">
      <!-- aba do item ativo: mesma cor do painel, encaixada nele; desliza entre os itens -->
      <span
        class="tab"
        aria-hidden="true"
        [class.on]="!!slider.box()"
        [class.animated]="slider.animated()"
        [style.transform]="'translateY(' + (slider.box()?.y ?? 0) + 'px)'"
        [style.height.px]="slider.box()?.h ?? 0"
      ></span>
      @for (item of items; track item.path) {
        <a
          #link
          [routerLink]="item.path"
          routerLinkActive="active"
          [routerLinkActiveOptions]="{ exact: !!item.exact }"
          ariaCurrentWhenActive="page"
          (isActiveChange)="slider.track(link, $event)"
        >
          <ww-icon [name]="item.icon" />
          {{ item.label }}
        </a>
      }
    </nav>

    @if (auth.user(); as user) {
      <section class="me" aria-label="Sua conta">
        <ww-avatar [user]="user" [size]="36" />
        <div>
          <strong>{{ user.displayName }}</strong>
          <span>&#64;{{ user.username }}</span>
        </div>
        <button type="button" (click)="logout()">Sair</button>
      </section>
    }

    <div class="credits">
      <a routerLink="/sobre">Sobre e créditos</a>
      <span>Dados de filmes e séries: TMDB</span>
    </div>
  `,
  styles: `
    :host {
      --tab-r: 16px;
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-6);
      height: 100%;
      padding: var(--ww-space-6) 0 var(--ww-space-6) var(--ww-space-4);
      background: var(--frame, var(--bg));
      color: var(--text);
    }
    .brand {
      padding: 0 var(--ww-space-3);
      text-decoration: none;
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    .brand:hover {
      transform: scale(1.04);
    }
    nav {
      position: relative;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    nav a {
      position: relative;
      z-index: 1;
      display: flex;
      align-items: center;
      gap: var(--ww-space-3);
      min-height: var(--ww-touch);
      margin-right: var(--ww-space-4);
      padding: 0 var(--ww-space-3);
      border-radius: var(--ww-radius-sm);
      color: var(--text-2);
      font-size: 15px;
      font-weight: 500;
      text-decoration: none;
      transition:
        background-color var(--ww-medium) var(--ww-ease-out),
        color var(--ww-medium) var(--ww-ease-out),
        margin var(--ww-medium) var(--ww-ease-out);
    }
    nav a ww-icon {
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    nav a:hover {
      background: var(--hover);
      color: var(--text);
    }
    nav a:hover ww-icon {
      transform: translateX(2px) scale(1.08);
    }
    /* o ativo encosta no painel (a aba vai até a borda) */
    nav a.active {
      margin-right: 0;
      background: transparent;
      color: var(--link);
      font-weight: 600;
    }
    .tab {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      border-radius: var(--ww-radius-md) 0 0 var(--ww-radius-md);
      background: var(--panel, var(--surface-2));
      opacity: 0;
      pointer-events: none;
    }
    .tab.on {
      opacity: 1;
    }
    .tab.animated {
      transition:
        transform var(--ww-slow) var(--ww-ease-out),
        height var(--ww-slow) var(--ww-ease-out),
        opacity var(--ww-medium) var(--ww-ease-out);
    }
    /* cantos invertidos acima e abaixo da aba, para ela "sair" do painel */
    .tab::before,
    .tab::after {
      content: '';
      position: absolute;
      right: 0;
      width: var(--tab-r);
      height: var(--tab-r);
    }
    .tab::before {
      top: calc(-1 * var(--tab-r));
      background: radial-gradient(circle at 0 0, transparent var(--tab-r), var(--panel) calc(var(--tab-r) + 0.5px));
    }
    .tab::after {
      bottom: calc(-1 * var(--tab-r));
      background: radial-gradient(circle at 0 100%, transparent var(--tab-r), var(--panel) calc(var(--tab-r) + 0.5px));
    }
    .me {
      display: flex;
      align-items: center;
      gap: var(--ww-space-3);
      margin-right: var(--ww-space-4);
      padding: var(--ww-space-3);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-md);
      background: var(--panel);
      transition: box-shadow var(--ww-medium) var(--ww-ease-out);
    }
    .me:hover {
      box-shadow: var(--ww-shadow-soft);
    }
    .me div {
      flex-grow: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      font-size: 13px;
    }
    .me span {
      color: var(--text-2);
    }
    .me button {
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-2);
      border: 0;
      border-radius: var(--ww-radius-sm);
      background: none;
      color: var(--link);
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: background-color var(--ww-medium) var(--ww-ease-out);
    }
    .me button:hover {
      background: var(--hover);
    }
    .credits {
      margin-top: auto;
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-1);
      padding: 0 var(--ww-space-3);
      font-size: 12px;
      color: var(--text-2);
    }
    .credits a {
      color: var(--text-2);
    }
  `,
})
export class SideNav {
  protected readonly items = SIDE_ITEMS;
  protected readonly slider = new ActiveSlider();
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/entrar']);
  }
}
