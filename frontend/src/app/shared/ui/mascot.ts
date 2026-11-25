import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Mascote: fantasminha pensando "o que eu vejo?".
 * - `scene`: fantasma + "?" (telas vazias, ícone do app, Sobre)
 * - `face`:  só o fantasma, recortado para caber pequeno (logo, favicon)
 * É decorativo: quem usa já tem um título que diz o que está acontecendo.
 */
@Component({
  selector: 'ww-mascot',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.border-radius.px]': 'size() * 0.24',
  },
  template: `<img [src]="src()" alt="" [width]="size()" [height]="size()" decoding="async" />`,
  styles: `
    :host {
      display: inline-block;
      flex-shrink: 0;
      overflow: hidden;
      background: var(--ww-mascot-indigo);
    }
    img {
      display: block;
      width: 100%;
      height: 100%;
    }
  `,
})
export class Mascot {
  readonly size = input(96);
  readonly variant = input<'scene' | 'face'>('scene');
  protected readonly src = computed(() =>
    this.variant() === 'face' ? 'mascot/mascot-face.svg' : 'mascot/mascot.svg',
  );
}
