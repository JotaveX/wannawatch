import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { ImageSet } from '../../core/api/models';
import { artFor } from './art';

/**
 * Imagem do título (pôster ou backdrop) com placeholder geométrico de fallback:
 * sem imagem ou se ela falhar, mostra cor chapada da paleta com círculos e o nome do título.
 *
 * A imagem é decorativa (alt=""): o nome do título sempre aparece em texto perto dela.
 */
@Component({
  selector: 'ww-poster',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': '"ratio-" + ratio()',
    '[style.background]': 'art().bg',
    '[style.color]': 'art().fg',
  },
  template: `
    @if (showImage()) {
      <img
        [src]="image()!.small"
        [attr.srcset]="image()!.small + ' 1x, ' + image()!.large + ' 2x'"
        alt=""
        [attr.loading]="eager() ? 'eager' : 'lazy'"
        decoding="async"
        (error)="failed.set(true)"
      />
    } @else {
      <span class="circle" [style.top.%]="art().circle.top" [style.left.%]="art().circle.left"
            [style.width]="art().circle.size + 'cqmin'" [style.background]="art().shape"></span>
      <span class="spot" [style.top.%]="art().spot.top" [style.left.%]="art().spot.left"
            [style.width]="art().spot.size + 'cqmin'" [style.background]="art().dot"></span>
      @if (showTitle()) {
        <span class="name" aria-hidden="true">{{ title() }}</span>
      }
    }
    <ng-content />
  `,
  styles: `
    :host {
      position: relative;
      display: block;
      overflow: hidden;
      border-radius: inherit;
      /* as formas são medidas pela menor dimensão (cqmin) */
      container-type: size;
    }
    :host(.ratio-poster) {
      aspect-ratio: 2 / 3;
    }
    :host(.ratio-backdrop) {
      aspect-ratio: 16 / 9;
    }
    :host(.ratio-fill) {
      width: 100%;
      height: 100%;
    }
    img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      max-width: none;
      object-fit: cover;
    }
    .circle,
    .spot {
      position: absolute;
      aspect-ratio: 1;
      border-radius: 50%;
    }
    .name {
      position: absolute;
      left: 10%;
      right: 10%;
      bottom: 8%;
      font-family: var(--ww-font-display);
      font-weight: 800;
      font-size: clamp(13px, 11cqmin, 34px);
      line-height: 1.02;
      letter-spacing: -0.02em;
      text-transform: uppercase;
    }
  `,
})
export class Poster {
  readonly image = input<ImageSet | null>(null);
  /** semente do placeholder (use o id do título) */
  readonly seed = input(0);
  readonly title = input('');
  readonly ratio = input<'poster' | 'backdrop' | 'fill'>('poster');
  readonly showTitle = input(true);
  readonly eager = input(false);

  protected readonly failed = signal(false);
  protected readonly art = computed(() => artFor(this.seed()));
  protected readonly showImage = computed(() => !!this.image() && !this.failed());
}
