import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SocialMock } from '../mocks/social.mock';
import { Icon } from '../shared/ui/icon';
import { Logo } from '../shared/ui/logo';
import { SIDE_ITEMS } from './nav-items';

/** Barra lateral do desktop, no Swipe, no Reels e nas telas secundárias (estilo TikTok web). */
@Component({
  selector: 'ww-side-nav',
  imports: [RouterLink, RouterLinkActive, Icon, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/" class="brand" aria-label="wannawatch? — início"><ww-logo [size]="26" /></a>

    <nav aria-label="Navegação principal">
      @for (item of items; track item.path) {
        <a
          [routerLink]="item.path"
          routerLinkActive="active"
          [routerLinkActiveOptions]="{ exact: !!item.exact }"
          ariaCurrentWhenActive="page"
        >
          <ww-icon [name]="item.icon" />
          {{ item.label }}
        </a>
      }
    </nav>

    <section class="group" aria-label="Seu grupo">
      <span class="avatar" aria-hidden="true">{{ social.user.initials }}</span>
      <div>
        <strong>{{ social.group.name }}</strong>
        <span>{{ social.group.members }} pessoas</span>
      </div>
    </section>

    <footer>
      <a routerLink="/sobre">Sobre e créditos</a>
      <span>Dados de filmes e séries: TMDB</span>
    </footer>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-6);
      height: 100%;
      padding: var(--ww-space-6) var(--ww-space-4);
      border-right: var(--ww-hairline) solid var(--border);
      background: var(--bg);
      color: var(--text);
    }
    .brand {
      padding: 0 var(--ww-space-3);
      text-decoration: none;
    }
    nav {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    nav a {
      display: flex;
      align-items: center;
      gap: var(--ww-space-3);
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-3);
      border-radius: var(--ww-radius-sm);
      color: var(--text);
      font-size: 15px;
      font-weight: 500;
      text-decoration: none;
    }
    nav a:hover {
      background: var(--surface-2);
    }
    nav a.active {
      background: var(--surface-2);
      color: var(--link);
      font-weight: 600;
    }
    .group {
      display: flex;
      align-items: center;
      gap: var(--ww-space-3);
      padding: var(--ww-space-3);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-md);
    }
    .group div {
      display: flex;
      flex-direction: column;
      font-size: 13px;
    }
    .group span:not(.avatar) {
      color: var(--text-2);
    }
    .avatar {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: var(--ww-radius-sm);
      background: var(--ww-yellow);
      color: var(--ww-bg-dark);
      font-family: var(--ww-font-display);
      font-weight: 700;
      font-size: 13px;
    }
    footer {
      margin-top: auto;
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-1);
      padding: 0 var(--ww-space-3);
      font-size: 12px;
      color: var(--text-2);
    }
    footer a {
      color: var(--text-2);
    }
  `,
})
export class SideNav {
  protected readonly items = SIDE_ITEMS;
  protected readonly social = inject(SocialMock);
}
