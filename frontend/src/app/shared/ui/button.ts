import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'light' | 'glass' | 'outline' | 'soft';

/**
 * Botão em pílula. Usa o elemento real (<button> ou <a>), só aplica o visual:
 *   <button wwButton variant="primary">Quero ver</button>
 *   <a wwButton variant="light" href="…">Assistir trailer</a>
 *
 * primary = pink (texto branco, #D70F45 por contraste) · light = branco · glass = escuro translúcido
 * outline = borda fina · soft = cinza de superfície
 */
@Component({
  selector: 'button[wwButton], a[wwButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ww-button',
    '[class]': '"ww-button--" + variant() + " ww-button--" + size()',
  },
  template: `<ng-content />`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--ww-space-2);
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-5);
      border: var(--ww-hairline) solid transparent;
      border-radius: var(--ww-radius-pill);
      font-family: var(--ww-font-ui);
      font-size: 15px;
      font-weight: 600;
      text-decoration: none;
      white-space: nowrap;
      cursor: pointer;
      transition:
        background-color var(--ww-medium) var(--ww-ease-out),
        border-color var(--ww-medium) var(--ww-ease-out),
        color var(--ww-medium) var(--ww-ease-out),
        box-shadow var(--ww-medium) var(--ww-ease-out),
        transform var(--ww-medium) var(--ww-ease-spring);
    }
    @media (hover: hover) and (pointer: fine) {
      :host(:hover:not(:disabled)) {
        transform: translateY(-1px);
      }
      :host(.ww-button--primary:hover:not(:disabled)),
      :host(.ww-button--light:hover:not(:disabled)) {
        box-shadow: var(--ww-shadow-soft);
      }
      :host(.ww-button--outline:hover:not(:disabled)) {
        background: var(--hover, rgba(127, 127, 127, 0.1));
      }
      :host(.ww-button--soft:hover:not(:disabled)) {
        background: var(--hover, var(--surface-2));
      }
    }
    :host(:active:not(:disabled)) {
      transform: scale(0.96);
      transition-duration: var(--ww-fast);
    }
    :host(:disabled) {
      opacity: 0.5;
      cursor: not-allowed;
    }
    :host(.ww-button--lg) {
      min-height: 48px;
      padding: 0 var(--ww-space-6);
      font-size: 16px;
    }
    :host(.ww-button--sm) {
      padding: 0 var(--ww-space-3);
      font-size: 13px;
    }
    :host(.ww-button--primary) {
      background: var(--ww-pink-strong);
      color: var(--ww-white);
    }
    :host(.ww-button--light) {
      background: var(--ww-white);
      color: var(--ww-bg-dark);
    }
    :host(.ww-button--glass) {
      background: var(--ww-glass-dark);
      color: var(--ww-white);
    }
    :host(.ww-button--outline) {
      background: transparent;
      border-color: currentColor;
      color: inherit;
    }
    :host(.ww-button--soft) {
      background: var(--surface-2);
      color: var(--text);
    }
  `,
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<'sm' | 'md' | 'lg'>('md');
}
