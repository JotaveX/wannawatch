import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  model,
  signal,
  viewChildren,
} from '@angular/core';

export interface SegmentOption<T extends string = string> {
  value: T;
  label: string;
}

/**
 * Seletor segmentado estilo iOS: fundo cinza, opção ativa branca com sombra sutil.
 *   <ww-segmented label="Modo" [options]="opts" [(value)]="mode" />
 *
 * variant "pill" = abas flutuantes do Reels (pílula branca, ativa em grafite).
 *
 * O fundo da opção ativa é um elemento só, que desliza até a opção escolhida.
 */
@Component({
  selector: 'ww-segmented',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'group',
    '[attr.aria-label]': 'label()',
    '[class.pill]': 'variant() === "pill"',
    '[class.measured]': '!!thumb()',
  },
  template: `
    <span
      class="thumb"
      aria-hidden="true"
      [class.on]="!!thumb()"
      [class.animated]="animated()"
      [style.transform]="'translateX(' + (thumb()?.x ?? 0) + 'px)'"
      [style.width.px]="thumb()?.w ?? 0"
    ></span>
    @for (opt of options(); track opt.value) {
      <button #btn type="button" [attr.aria-pressed]="value() === opt.value" (click)="value.set(opt.value)">
        {{ opt.label }}
      </button>
    }
  `,
  styles: `
    :host {
      position: relative;
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
      position: relative;
      z-index: 1;
      transition:
        color var(--ww-medium) var(--ww-ease-out),
        opacity var(--ww-medium) var(--ww-ease-out);
    }
    button[aria-pressed='false']:hover {
      opacity: 0.7;
    }
    button:active {
      transform: scale(0.96);
    }
    .thumb {
      position: absolute;
      top: 3px;
      bottom: 3px;
      left: 0;
      border-radius: 8px;
      background: var(--ww-white);
      box-shadow: var(--ww-shadow-segment);
      opacity: 0;
    }
    .thumb.on {
      opacity: 1;
    }
    .thumb.animated {
      transition:
        transform var(--ww-slow) var(--ww-ease-out),
        width var(--ww-slow) var(--ww-ease-out);
    }
    /* sem medida (ex.: testes, primeira pintura): cai no fundo direto no botão */
    :host(:not(.measured)) button[aria-pressed='true'] {
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
    :host(.pill) .thumb {
      border-radius: var(--ww-radius-pill);
      background: var(--ww-graphite);
      box-shadow: none;
    }
    :host(.pill) button[aria-pressed='true'] {
      color: var(--ww-white);
    }
    :host(.pill:not(.measured)) button[aria-pressed='true'] {
      background: var(--ww-graphite);
      box-shadow: none;
    }
  `,
})
export class Segmented<T extends string = string> {
  readonly options = input.required<SegmentOption<T>[]>();
  readonly value = model.required<T>();
  readonly label = input.required<string>();
  readonly variant = input<'ios' | 'pill'>('ios');

  private readonly buttons = viewChildren<ElementRef<HTMLButtonElement>>('btn');
  protected readonly thumb = signal<{ x: number; w: number } | null>(null);
  /** a primeira posição entra sem deslizar */
  protected readonly animated = signal(false);

  constructor() {
    afterRenderEffect(() => {
      const i = this.options().findIndex((o) => o.value === this.value());
      const el = this.buttons()[i]?.nativeElement;
      if (!el?.offsetWidth) {
        this.thumb.set(null);
        return;
      }
      const prev = this.thumb();
      if (prev?.x === el.offsetLeft && prev.w === el.offsetWidth) return;
      this.thumb.set({ x: el.offsetLeft, w: el.offsetWidth });
      if (!this.animated()) requestAnimationFrame(() => requestAnimationFrame(() => this.animated.set(true)));
    });
  }
}
