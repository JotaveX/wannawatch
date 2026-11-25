import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HomeHero as Hero } from '../../core/api/models';
import { length, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { LikesStore, likedByLabel } from '../../core/social/likes-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { AppMark } from '../../shared/ui/logo';
import { Poster } from '../../shared/ui/poster';

/**
 * Destaque da Home. A imagem recebe uma camada chapada de #141416 a 60% por inteiro:
 * no pior caso (pixel branco) o fundo vira ~#727272, e o texto branco ainda fica acima de 4,5:1.
 */
@Component({
  selector: 'ww-home-hero',
  imports: [RouterLink, Button, Icon, AppMark, Poster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ww-poster class="bg" ratio="fill" [image]="hero().backdrop" [widths]="[780, 1280]" sizes="100vw"
               [seed]="hero().id" [showTitle]="false" [eager]="true" />
    <div class="shade" aria-hidden="true"></div>

    <ng-content />

    <div class="content ww-stagger">
      <p class="kind"><ww-app-mark [size]="22" />{{ type() }}</p>
      <h1>
        <span class="title" [class.long]="hero().title.length > 18">{{ hero().title }}</span>
        @if (hero().tagline; as tagline) {
          <span class="tagline">{{ tagline }}</span>
        }
      </h1>
      <ul class="meta" aria-label="Informações">
        @for (part of meta(); track part; let first = $first) {
          <li [class.sep]="!first">{{ part }}</li>
        }
        @if (hero().ageRating; as rating) {
          <li class="rating" [attr.aria-label]="'Classificação ' + rating">{{ rating }}</li>
        }
      </ul>
      @if (hero().overview; as overview) {
        <p class="overview">{{ overview }}</p>
      }
      @if (reason(); as reason) {
        <p class="reason">{{ reason }}</p>
      }
      <div class="actions">
        @if (hero().trailerUrl; as trailer) {
          <a wwButton variant="light" size="lg" [href]="trailer" target="_blank" rel="noopener">
            <ww-icon name="play" [size]="16" />Assistir trailer<span class="visually-hidden"> (abre o YouTube)</span>
          </a>
        }
        <a wwButton variant="glass" size="lg" [routerLink]="link()">
          <ww-icon name="info" [size]="18" [stroke]="2.2" />Mais informações
        </a>
      </div>
    </div>
  `,
  styles: `
    :host {
      position: relative;
      display: flex;
      flex-direction: column;
      min-height: min(680px, 92dvh);
      overflow: hidden;
      background: var(--ww-bg-dark);
      color: var(--ww-white);
    }
    .bg {
      position: absolute !important;
      inset: 0;
      border-radius: 0;
      animation: hero-settle 1.6s var(--ww-ease-out) both;
    }
    /* o fundo chega um pouco ampliado e assenta */
    @keyframes hero-settle {
      from {
        opacity: 0;
        transform: scale(1.06);
      }
    }
    .shade {
      position: absolute;
      inset: 0;
      background: rgba(20, 20, 22, 0.6);
    }
    .content {
      position: relative;
      z-index: 1;
      flex-grow: 1;
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 14px;
      max-width: 680px;
      padding: var(--ww-space-6) var(--ww-page-x) 150px;
    }
    .kind {
      display: flex;
      align-items: center;
      gap: var(--ww-space-2);
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.16em;
      text-transform: uppercase;
    }
    h1 {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .title {
      font-weight: 800;
      font-size: clamp(40px, 7vw, 92px);
      line-height: 0.9;
      letter-spacing: var(--ww-tracking-huge);
      text-transform: uppercase;
      text-wrap: balance;
      /* só quebra no meio se uma palavra sozinha não couber */
      overflow-wrap: break-word;
    }
    .title.long {
      font-size: clamp(34px, 5.2vw, 68px);
    }
    .overview,
    .reason,
    .meta {
      max-width: 520px;
    }
    .tagline {
      font-weight: 600;
      font-size: clamp(18px, 2.4vw, 28px);
      letter-spacing: -0.03em;
      text-transform: uppercase;
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 10px;
      padding: 0;
      margin: 0;
      list-style: none;
      font-size: 14px;
      font-weight: 500;
    }
    .sep::before {
      content: '·' / '';
      margin-right: 10px;
    }
    .rating {
      padding: 2px 6px;
      border: var(--ww-hairline) solid currentColor;
      border-radius: 4px;
      font-size: 12px;
    }
    .overview {
      font-size: 17px;
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 4;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .reason {
      font-size: 14px;
      font-weight: 500;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 6px;
    }
    @media (max-width: 767px) {
      :host {
        min-height: 600px;
      }
      .content {
        justify-content: flex-end;
        padding-bottom: 130px;
      }
      .overview {
        font-size: 15px;
        -webkit-line-clamp: 3;
      }
    }
  `,
})
export class HomeHero {
  readonly hero = input.required<Hero>();
  private readonly likes = inject(LikesStore);

  protected readonly type = computed(() => typeLabel(this.hero()));
  protected readonly link = computed(() => titleLink(this.hero()));
  /** "Bia e Caio curtiram", se alguém curtiu */
  protected readonly reason = computed(() => likedByLabel(this.likes.friends(this.hero().id)));
  protected readonly meta = computed(() => {
    const h = this.hero();
    return [h.genres[0]?.name, h.year ? String(h.year) : null, length(h)].filter((p): p is string => !!p);
  });
}
