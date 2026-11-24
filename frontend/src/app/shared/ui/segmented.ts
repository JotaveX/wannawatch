import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface SegmentOption<T extends string = string> {
  value: T;
  label: string;
}

/**
 * Seletor segmentado estilo iOS: fundo cinza, opção ativa branca com sombra sutil.
 *   <ww-segmented label="Modo" [options]="opts" [(value)]="mode" />
 *
 * variant "pill" = abas flutuantes do Reels (pílula branca, ativa em grafite).
 */
@Component({
  selector: 'ww-segmented',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'group', '[attr.aria-label]': 'label()', '[class.pill]': 'variant() === "pill"' },
  template: `
    @for (opt of options(); track opt.value) {
      <button type="button" [attr.aria-pressed]="value() === opt.value" (click)="value.set(opt.value)">
        {{ opt.label }}
      </button>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      padding: 3px;
      border-radius: var(--ww-radius-sm);
      background: var(--ww-surface-gray);
    }
    button {
      min-height: 38px;
      padding: 0 var(--ww-space-3);
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: var(--ww-graphite);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: background-color var(--ww-fast) var(--ww-ease);
    }
    button[aria-pressed='true'] {
      background: var(--ww-white);
      box-shadow: var(--ww-shadow-segment);
    }
    :host(.pill) {
      border-radius: var(--ww-radius-pill);
      background: var(--ww-glass-light);
    }
    :host(.pill) button {
      border-radius: var(--ww-radius-pill);
      padding: 0 var(--ww-space-4);
    }
    :host(.pill) button[aria-pressed='true'] {
      background: var(--ww-graphite);
      color: var(--ww-white);
      box-shadow: none;
    }
  `,
})
export class Segmented<T extends string = string> {
  readonly options = input.required<SegmentOption<T>[]>();
  readonly value = model.required<T>();
  readonly label = input.required<string>();
  readonly variant = input<'ios' | 'pill'>('ios');
}
