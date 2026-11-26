import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthStore } from '../core/auth/auth-store';
import { Avatar } from '../shared/ui/avatar';
import { Icon } from '../shared/ui/icon';
import { Logo } from '../shared/ui/logo';
import { ActiveSlider } from './active-slider';

/**
 * Menu do topo das telas "cinema" (Home, detalhes, busca), por cima da imagem.
 * Desktop: logo · pílula escura com busca e links · avatar.
 * Abaixo de 1024px os links somem (a navegação fica na barra de abas).
 */
@Component({
  selector: 'ww-top-nav',
  imports: [RouterLink, RouterLinkActive, Avatar, Icon, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.solid]': '!overImage()', '(window:resize)': 'slider.measure()' },
  template: `
    <header>
      <a routerLink="/" class="brand" aria-label="wannawatch? — início"><ww-logo [size]="26" [mark]="true" /></a>

      <nav aria-label="Seções">
        <span
          class="pill"
          aria-hidden="true"
          [class.on]="!!slider.box()"
          [class.animated]="slider.animated()"
          [style.transform]="'translateX(' + (slider.box()?.x ?? 0) + 'px)'"
          [style.width.px]="slider.box()?.w ?? 0"
        ></span>
        <div class="items">
          <a #search routerLink="/busca" class="search" aria-label="Buscar" routerLinkActive="active" ariaCurrentWhenActive="page"
             (isActiveChange)="track(search, $event)">
            <ww-icon name="search" [size]="18" [stroke]="2.2" />
          </a>
          @for (link of links; track link.path) {
            <a
              #a
              class="link"
              [routerLink]="link.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: link.path === '/' }"
              ariaCurrentWhenActive="page"
              (isActiveChange)="track(a, $event)"
            >{{ link.label }}</a>
          }
        </div>
      </nav>

      @if (auth.user(); as user) {
        <a routerLink="/perfil" class="avatar" [attr.aria-label]="'Perfil de ' + user.displayName">
          <ww-avatar [user]="user" [size]="44" />
        </a>
      }
    </header>
  `,
  styles: `
    header {
      view-transition-name: ww-top-nav;
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
      position: relative;
      display: flex;
      align-items: center;
      gap: 2px;
      padding: 4px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-glass-dark);
      transition: background-color var(--ww-medium) var(--ww-ease-out);
    }
    /* fundo branco do link ativo: desliza de um link para o outro */
    .pill {
      position: absolute;
      top: 4px;
      bottom: 4px;
      left: 0;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-white);
      opacity: 0;
      pointer-events: none;
    }
    /*
     * O menu é recriado a cada página: entre telas, quem desliza a pílula é a View Transition.
     * Só ganha nome quando está visível — sem link ativo à mostra (ex.: Home no celular, onde
     * "Início" fica escondido) não há de onde deslizar, e ela só aparece no lugar.
     */
    .pill.on {
      opacity: 1;
      view-transition-name: ww-nav-pill;
    }
    .pill.animated {
      transition:
        transform var(--ww-slow) var(--ww-ease-out),
        width var(--ww-slow) var(--ww-ease-out),
        opacity var(--ww-medium) var(--ww-ease-out);
    }
    /*
     * Os links ganham camada própria na View Transition, posta acima da pílula em motion.css
     * (z-index no grupo). Sem isso a pílula branca cobre o link ativo até a animação acabar.
     * Sem position aqui de propósito: o offsetParent dos links continua sendo o nav, que é
     * onde a pílula se posiciona.
     */
    .items {
      display: flex;
      align-items: center;
      gap: 2px;
      view-transition-name: ww-nav-items;
    }
    nav a {
      position: relative;
      z-index: 1;
      transition:
        color var(--ww-medium) var(--ww-ease-out),
        background-color var(--ww-medium) var(--ww-ease-out);
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
    nav a:not(.active):hover {
      background: rgba(255, 255, 255, 0.12);
    }
    nav a.active {
      color: var(--ww-bg-dark);
      font-weight: 600;
    }
    .avatar {
      display: grid;
      place-items: center;
      width: var(--ww-touch);
      height: var(--ww-touch);
      border-radius: 50%;
      text-decoration: none;
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    .avatar:hover {
      transform: scale(1.06) rotate(-3deg);
    }
    /* sem imagem atrás, o vidro escuro sumiria no fundo escuro */
    :host(.solid) nav {
      background: var(--ww-surface-dark);
    }
    @media (min-width: 1024px) {
      nav .link {
        display: inline-flex;
      }
      /*
       * Nas telas com barra lateral (ex.: Séries) o logo já está lá: some daqui, mas continua
       * ocupando o lugar para o menu não mudar de posição.
       */
      :host-context(.shell--feed) .brand {
        visibility: hidden;
      }
    }
    /* celular: logo · busca · avatar, numa linha só */
    @media (max-width: 1023px) {
      header {
        flex-wrap: nowrap;
        gap: var(--ww-space-2);
      }
      .brand {
        margin-right: auto;
      }
    }
  `,
})
export class TopNav {
  protected readonly slider = new ActiveSlider();
  /** false quando não há imagem atrás (busca, erros): a pílula ganha fundo sólido */
  readonly overImage = input(true);
  protected readonly auth = inject(AuthStore);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly links = [
    { label: 'Início', path: '/' },
    { label: 'Séries', path: '/series' },
    { label: 'Filmes', path: '/filmes' },
    { label: 'Swipe', path: '/swipe' },
    { label: 'Reels', path: '/reels' },
    { label: 'Match', path: '/match' },
    { label: 'Minha lista', path: '/minha-lista' },
  ];

  /**
   * O routerLinkActive avisa num microtask, logo depois da página nova ser desenhada. Na troca de
   * página o navegador fotografa a tela logo em seguida: sem desenhar a pílula aqui mesmo, ela
   * ainda não teria posição nem nome na foto e entraria no fade (cinza) em vez de deslizar.
   */
  protected track(el: HTMLElement, active: boolean): void {
    this.slider.track(el, active);
    if (!this.destroyRef.destroyed) this.cdr.detectChanges();
  }
}
