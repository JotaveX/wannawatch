import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SocialMock } from '../mocks/social.mock';
import { Icon } from '../shared/ui/icon';
import { Logo } from '../shared/ui/logo';

/**
 * Menu do topo das telas "cinema" (Home, detalhes, busca), por cima da imagem.
 * Desktop: logo · pílula escura com busca e links · modo grupo/solo e avatar.
 * Abaixo de 1024px os links somem (a navegação fica na barra de abas).
 */
@Component({
  selector: 'ww-top-nav',
  imports: [RouterLink, RouterLinkActive, Icon, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header>
      <a routerLink="/" class="brand" aria-label="wannawatch? — início"><ww-logo [size]="26" /></a>

      <nav aria-label="Seções">
        <a routerLink="/busca" class="search" aria-label="Buscar" routerLinkActive="active" ariaCurrentWhenActive="page">
          <ww-icon name="search" [size]="18" [stroke]="2.2" />
        </a>
        @for (link of links; track link.path) {
          <a
            class="link"
            [routerLink]="link.path"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: link.path === '/' }"
            ariaCurrentWhenActive="page"
          >{{ link.label }}</a>
        }
      </nav>

      <div class="me">
        <button type="button" class="mode" (click)="toggleMode()"
                [attr.aria-label]="'Modo: ' + (social.isGroup() ? social.group.name : 'só eu') + '. Trocar'">
          <span class="dot" [class.solo]="!social.isGroup()" aria-hidden="true"></span>
          <span class="mode-text">{{ social.isGroup() ? social.group.name : 'Só eu' }}</span>
        </button>
        <a routerLink="/perfil" class="avatar" aria-label="Perfil">{{ social.user.initials }}</a>
      </div>
    </header>
  `,
  styles: `
    header {
      position: relative;
      z-index: 3;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--ww-space-4);
      padding: 22px var(--ww-page-x);
      color: var(--ww-white);
    }
    .brand {
      color: inherit;
      text-decoration: none;
      --text: var(--ww-white);
    }
    nav {
      display: flex;
      align-items: center;
      gap: 2px;
      padding: 4px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-glass-dark);
    }
    nav a {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: var(--ww-touch);
      border-radius: var(--ww-radius-pill);
      color: var(--ww-white);
      text-decoration: none;
      font-size: 14px;
      font-weight: 500;
    }
    .search {
      width: var(--ww-touch);
    }
    nav .link {
      display: none;
      padding: 0 14px;
    }
    nav a.active {
      background: var(--ww-white);
      color: var(--ww-bg-dark);
      font-weight: 600;
    }
    .me {
      display: flex;
      align-items: center;
      gap: var(--ww-space-2);
    }
    .mode {
      display: inline-flex;
      align-items: center;
      gap: var(--ww-space-2);
      min-height: var(--ww-touch);
      padding: 0 14px;
      border: 0;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-glass-dark);
      color: var(--ww-white);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--ww-yellow);
    }
    .dot.solo {
      background: var(--ww-link-on-dark);
    }
    .avatar {
      display: grid;
      place-items: center;
      width: var(--ww-touch);
      height: var(--ww-touch);
      border-radius: var(--ww-radius-sm);
      background: var(--ww-yellow);
      color: var(--ww-bg-dark);
      font-family: var(--ww-font-display);
      font-weight: 700;
      font-size: 14px;
      text-decoration: none;
    }
    @media (min-width: 1024px) {
      nav .link {
        display: inline-flex;
      }
    }
    /* celular: logo · busca · modo (só a bolinha) · avatar, numa linha só */
    @media (max-width: 1023px) {
      header {
        flex-wrap: nowrap;
        gap: var(--ww-space-2);
      }
      .brand {
        margin-right: auto;
      }
    }
    @media (max-width: 479px) {
      .mode {
        width: var(--ww-touch);
        padding: 0;
        justify-content: center;
      }
      .mode-text {
        display: none;
      }
      .dot {
        width: 10px;
        height: 10px;
      }
    }
  `,
})
export class TopNav {
  protected readonly social = inject(SocialMock);
  protected readonly links = [
    { label: 'Início', path: '/' },
    { label: 'Séries', path: '/series' },
    { label: 'Filmes', path: '/filmes' },
    { label: 'Swipe', path: '/swipe' },
    { label: 'Reels', path: '/reels' },
    { label: 'Minha lista', path: '/minha-lista' },
  ];

  protected toggleMode(): void {
    this.social.mode.update((m) => (m === 'group' ? 'solo' : 'group'));
  }
}
