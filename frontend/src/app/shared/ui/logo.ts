import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Mascot } from './mascot';

/**
 * "wannawatch" em Inter Tight 700, espaçamento apertado, com o "?" em pink.
 * Com `mark`, o fantasminha aparece antes do nome (cabeçalhos e login).
 */
@Component({
  selector: 'ww-logo',
  imports: [Mascot],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[style.font-size.px]': 'size()' },
  template: `@if (mark()) {<ww-mascot class="mark" variant="face" [size]="round(size() * 1.25)" />}<span>wannawatch<span class="q">?</span></span>`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 0.32em;
      font-family: var(--ww-font-display);
      font-weight: 700;
      letter-spacing: var(--ww-tracking-logo);
      line-height: 1;
      color: var(--text);
      white-space: nowrap;
    }
    .q {
      color: var(--ww-pink);
    }
  `,
})
export class Logo {
  readonly size = input(24);
  readonly mark = input(false);
  protected readonly round = Math.round;
}

/** Ícone do app: quadrado arredondado pink com "?" branco. */
@Component({
  selector: 'ww-app-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() * 0.68',
    '[style.border-radius.px]': 'size() * 0.26',
  },
  template: `?`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      background: var(--ww-pink);
      color: var(--ww-white);
      font-family: var(--ww-font-display);
      font-weight: 800;
      line-height: 1;
    }
  `,
})
export class AppMark {
  readonly size = input(22);
}
