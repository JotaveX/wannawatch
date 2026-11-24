import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Selo "92% match" (o número vem do SocialMock por enquanto). */
@Component({
  selector: 'ww-match-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `{{ percent() }}% match`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      padding: 6px 12px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-white);
      color: var(--ww-graphite);
      font-size: 13px;
      font-weight: 600;
      white-space: nowrap;
    }
  `,
})
export class MatchBadge {
  readonly percent = input.required<number>();
}
