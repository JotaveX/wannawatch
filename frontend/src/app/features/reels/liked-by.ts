import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Friend, SocialMock } from '../../mocks/social.mock';
import { Icon } from '../../shared/ui/icon';

/**
 * Esferas flutuantes com os amigos que também curtiram (mock), no canto inferior esquerdo do reel.
 * Balançam devagar, cada uma num tempo; param com "reduzir movimento".
 */
@Component({
  selector: 'ww-liked-by',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.none]': '!friends().length' },
  template: `
    @if (friends().length) {
      <span class="spheres" aria-hidden="true">
        @for (f of friends(); track f.name; let i = $index) {
          <span class="sphere" [style.background]="f.bg" [style.color]="f.fg" [style.animation-delay]="i * -0.9 + 's'">
            {{ f.initials }}
            <span class="heart"><ww-icon name="heart-filled" [size]="9" [stroke]="0" /></span>
          </span>
        }
      </span>
      <span class="label">{{ label() }}</span>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: 44px;
    }
    :host(.none) {
      display: none;
    }
    .spheres {
      display: flex;
    }
    .sphere {
      position: relative;
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      margin-right: -8px;
      border: 2px solid var(--ww-bg-dark);
      border-radius: 50%;
      font-family: var(--ww-font-display);
      font-weight: 700;
      font-size: 13px;
      letter-spacing: -0.02em;
      animation: float 2.8s ease-in-out infinite;
    }
    .heart {
      position: absolute;
      right: -3px;
      bottom: -3px;
      display: grid;
      place-items: center;
      width: 17px;
      height: 17px;
      border: 2px solid var(--ww-bg-dark);
      border-radius: 50%;
      background: var(--ww-pink);
      color: var(--ww-white);
    }
    .label {
      margin-left: 8px;
      font-size: 13px;
      font-weight: 600;
    }
    @keyframes float {
      0%,
      100% {
        transform: translateY(0);
      }
      50% {
        transform: translateY(-5px);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .sphere {
        animation: none;
      }
    }
  `,
})
export class LikedBy {
  readonly titleId = input.required<number>();
  private readonly social = inject(SocialMock);
  protected readonly friends = computed<Friend[]>(() => this.social.friendsWhoLiked(this.titleId()));
  protected readonly label = computed(() => this.social.likedByLabel(this.friends()));
}
