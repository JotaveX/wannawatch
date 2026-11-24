import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconButtonVariant = 'outline' | 'pink' | 'glass' | 'light' | 'ghost';

/**
 * Botão redondo de ação, só com ícone. O nome acessível é obrigatório (label):
 *   <button wwIconButton label="Passar" size="lg"><ww-icon name="x" /></button>
 *
 * Tamanhos: sm 44px (mínimo de toque) · md 52px · lg 64px.
 */
@Component({
  selector: 'button[wwIconButton], a[wwIconButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ww-icon-button',
    '[class]': '"ww-icon-button--" + variant() + " ww-icon-button--" + size()',
    '[attr.aria-label]': 'label()',
    '[attr.title]': 'label()',
  },
  template: `<ng-content />`,
  styles: `
    :host {
      --size: 44px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      width: var(--size);
      height: var(--size);
      padding: 0;
      border: var(--ww-hairline) solid transparent;
      border-radius: 50%;
      cursor: pointer;
      text-decoration: none;
      transition: background-color var(--ww-fast) var(--ww-ease), transform var(--ww-fast) var(--ww-ease);
    }
    :host(:active) {
      transform: scale(0.94);
    }
    :host(:disabled) {
      opacity: 0.4;
      cursor: not-allowed;
    }
    :host(.ww-icon-button--md) {
      --size: 52px;
    }
    :host(.ww-icon-button--lg) {
      --size: 64px;
    }
    :host(.ww-icon-button--outline) {
      background: var(--ww-white);
      border-color: var(--ww-border);
      color: var(--ww-graphite);
    }
    :host(.ww-icon-button--pink) {
      background: var(--ww-pink);
      color: var(--ww-white);
    }
    :host(.ww-icon-button--glass) {
      background: var(--ww-glass-dark);
      color: var(--ww-white);
    }
    :host(.ww-icon-button--light) {
      background: var(--ww-glass-light);
      color: var(--ww-graphite);
    }
    :host(.ww-icon-button--ghost) {
      background: transparent;
      color: inherit;
    }
  `,
})
export class IconButton {
  readonly label = input.required<string>();
  readonly variant = input<IconButtonVariant>('outline');
  readonly size = input<'sm' | 'md' | 'lg'>('sm');
}
