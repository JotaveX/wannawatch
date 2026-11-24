import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TitleCard } from '../../core/api/models';
import { titleLink } from '../../core/title-link';
import { Poster } from './poster';

/** Item já pronto para o card: o título + o que aparece embaixo + selo/progresso opcionais. */
export interface RowItem {
  card: TitleCard;
  sub: string;
  badge?: string;
  /** 0–100, barra do "continue assistindo" */
  progress?: number;
}

/** Card horizontal (backdrop 16:9) com nome e linha de informação embaixo. */
@Component({
  selector: 'ww-wide-card',
  imports: [RouterLink, Poster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a [routerLink]="link()">
      <ww-poster
        class="art"
        ratio="backdrop"
        [image]="item().card.backdrop ?? item().card.poster"
        [widths]="item().card.backdrop ? [780, 1280] : [342, 780]"
        sizes="280px"
        [seed]="item().card.id"
        [title]="item().card.title"
      >
        @if (item().badge; as badge) {
          <span class="badge">{{ badge }}</span>
        }
        @if (item().progress !== undefined) {
          <span class="progress" role="progressbar" aria-label="Progresso"
                [attr.aria-valuenow]="item().progress" aria-valuemin="0" aria-valuemax="100">
            <span [style.width.%]="item().progress"></span>
          </span>
        }
      </ww-poster>
      <span class="name">{{ item().card.title }}</span>
      <span class="sub">{{ item().sub }}</span>
    </a>
  `,
  styles: `
    :host {
      flex-shrink: 0;
      width: clamp(200px, 62vw, 280px);
      scroll-snap-align: start;
    }
    a {
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: var(--ww-white);
      text-decoration: none;
    }
    .art {
      margin-bottom: 4px;
      border-radius: var(--ww-radius-sm);
      transition: transform var(--ww-fast) var(--ww-ease);
    }
    a:hover .art {
      transform: scale(1.02);
    }
    .name {
      font-size: 14px;
      font-weight: 600;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    .sub {
      font-size: 13px;
      color: var(--ww-text-on-dark-2);
    }
    .badge {
      position: absolute;
      top: 10px;
      left: 10px;
      padding: 4px 8px;
      border-radius: 6px;
      background: var(--ww-white);
      color: var(--ww-bg-dark);
      font-size: 12px;
      font-weight: 700;
    }
    .progress {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 4px;
      background: rgba(20, 20, 22, 0.6);
    }
    .progress span {
      display: block;
      height: 100%;
      background: var(--ww-pink);
    }
  `,
})
export class WideCard {
  readonly item = input.required<RowItem>();
  protected readonly link = computed(() => titleLink(this.item().card));
}
