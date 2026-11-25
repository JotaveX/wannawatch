import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TitleCard } from '../../core/api/models';
import { titleLink } from '../../core/title-link';
import { Poster } from '../../shared/ui/poster';

/** Card do Top 10: número grande vazado + pôster. */
@Component({
  selector: 'ww-top-card',
  imports: [RouterLink, Poster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a [routerLink]="link()" [attr.aria-label]="rank() + 'º lugar: ' + card().title">
      <span class="rank" [class.wide]="rank() >= 10" aria-hidden="true">{{ rank() }}</span>
      <ww-poster class="art" [image]="card().poster" [widths]="[342, 780]" sizes="124px"
                 [seed]="card().id" [title]="card().title" />
    </a>
  `,
  styles: `
    :host {
      flex-shrink: 0;
      scroll-snap-align: start;
    }
    a {
      display: flex;
      align-items: flex-end;
      height: 190px;
      text-decoration: none;
    }
    .rank {
      width: 96px;
      margin-right: -18px;
      font-family: var(--ww-font-display);
      font-weight: 800;
      font-size: 180px;
      line-height: 0.76;
      letter-spacing: -0.08em;
      text-align: right;
      color: var(--ww-bg-dark);
      -webkit-text-stroke: 3px #8e8e93;
    }
    .rank.wide {
      width: 150px;
    }
    .art {
      position: relative;
      width: 124px;
      border-radius: var(--ww-radius-sm);
      font-size: 14px;
      transition:
        transform var(--ww-medium) var(--ww-ease-spring),
        box-shadow var(--ww-medium) var(--ww-ease-out);
    }
    .rank {
      transition: -webkit-text-stroke-color var(--ww-medium) var(--ww-ease-out), transform var(--ww-slow) var(--ww-ease-out);
    }
    @media (hover: hover) and (pointer: fine) {
      a:hover .art {
        transform: translateY(-4px) rotate(-1deg);
        box-shadow: var(--ww-shadow-lift);
      }
      a:hover .rank {
        -webkit-text-stroke-color: var(--ww-white);
        transform: translateX(-4px);
      }
    }
    a:active .art {
      transform: scale(0.98);
      transition-duration: var(--ww-fast);
    }
  `,
})
export class TopCard {
  readonly card = input.required<TitleCard>();
  readonly rank = input.required<number>();
  protected readonly link = computed(() => titleLink(this.card()));
}
