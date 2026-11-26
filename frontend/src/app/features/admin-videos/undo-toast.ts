import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Aviso escuro em pílula com "Desfazer". Quem posiciona (fixo, embaixo) e anima é a página;
 * o texto é anunciado por uma região aria-live da própria página.
 */
@Component({
  selector: 'ww-undo-toast',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span>{{ text() }}</span>
    <button type="button" (click)="undo.emit()">Desfazer</button>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: var(--ww-space-4);
      max-width: calc(100vw - 32px);
      padding: 4px 4px 4px var(--ww-space-5);
      border-radius: var(--ww-radius-pill);
      background: var(--ww-graphite);
      color: var(--ww-white);
      box-shadow: var(--ww-shadow-lift);
      font-size: 14px;
    }
    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    button {
      flex: none;
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-4);
      border: 0;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-white);
      color: var(--ww-graphite);
      font: 600 14px var(--ww-font-ui);
      cursor: pointer;
    }
    button:focus-visible {
      outline: 3px solid var(--ww-white);
      outline-offset: 2px;
    }
  `,
})
export class UndoToast {
  readonly text = input.required<string>();
  readonly undo = output<void>();
}
