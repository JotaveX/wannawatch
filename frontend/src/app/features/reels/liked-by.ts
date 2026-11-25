import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { UserRef } from '../../core/api/models';
import { LikesStore, likedByLabel } from '../../core/social/likes-store';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';

/**
 * Esferas flutuantes com os amigos que curtiram, no canto inferior esquerdo do reel.
 * Balançam devagar, cada uma num tempo; param com "reduzir movimento".
 */
@Component({
  selector: 'ww-liked-by',
  imports: [Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.none]': '!friends().length' },
  template: `
    @if (friends().length) {
      <span class="spheres" aria-hidden="true">
        @for (f of shown(); track f.id; let i = $index) {
          <ww-avatar class="sphere" [user]="f" [size]="40" [style.animation-delay]="i * -0.9 + 's'">
            <span class="heart"><ww-icon name="heart-filled" [size]="9" [stroke]="0" /></span>
          </ww-avatar>
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
      margin-right: -8px;
      border: 2px solid var(--ww-bg-dark);
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
  private readonly likes = inject(LikesStore);
  protected readonly friends = computed<UserRef[]>(() => this.likes.friends(this.titleId()));
  /** no máximo 3 esferas; o texto conta o resto */
  protected readonly shown = computed(() => this.friends().slice(0, 3));
  protected readonly label = computed(() => likedByLabel(this.friends()));
}
