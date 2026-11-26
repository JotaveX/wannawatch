import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { UserRef } from '../../core/api/models';
import { LikesStore, likedByLabel } from '../../core/social/likes-store';
import { Avatar } from '../../shared/ui/avatar';
import { Icon } from '../../shared/ui/icon';

/**
 * Esferas com os amigos que curtiram. Balançam devagar, cada uma num tempo; param com "reduzir
 * movimento". Sem amigos, mostra só o motivo da recomendação, quando houver.
 *
 * `floating` (no reel): só as esferas, sem texto; o nome aparece ao passar o mouse, focar ou tocar
 * na esfera. Para leitores de tela o texto continua lá (escondido), porque descreve o reel.
 */
@Component({
  selector: 'ww-liked-by',
  imports: [Avatar, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.none]': '!label()', '[class.floating]': 'floating()' },
  template: `
    @if (friends().length) {
      @if (floating()) {
        <span class="visually-hidden">{{ label() }}</span>
        <ul class="spheres">
          @for (f of shown(); track f.id; let i = $index) {
            <li [style.animation-delay]="i * -0.9 + 's'">
              <button
                type="button"
                class="who"
                [attr.aria-label]="f.displayName + ' curtiu'"
                (click)="open.set(open() === f.id ? null : f.id)"
                (blur)="open.set(null)"
              >
                <ww-avatar class="sphere" [user]="f" [size]="40">
                  <span class="heart"><ww-icon name="heart-filled" [size]="9" [stroke]="0" /></span>
                </ww-avatar>
              </button>
              <span class="tip" [class.open]="open() === f.id" aria-hidden="true">
                {{ f.displayName }}
              </span>
            </li>
          }
          @if (rest()) {
            <li class="more" aria-hidden="true">+{{ rest() }}</li>
          }
        </ul>
      } @else {
        <span class="spheres" aria-hidden="true">
          @for (f of shown(); track f.id; let i = $index) {
            <ww-avatar
              class="sphere"
              [user]="f"
              [size]="40"
              [style.animation-delay]="i * -0.9 + 's'"
            >
              <span class="heart"><ww-icon name="heart-filled" [size]="9" [stroke]="0" /></span>
            </ww-avatar>
          }
        </span>
        <span class="label">{{ label() }}</span>
      }
    } @else if (reason()) {
      <span class="label reason">{{ reason() }}</span>
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
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .sphere {
      margin-right: -8px;
      border: 2px solid var(--ww-bg-dark);
      animation: float 2.8s ease-in-out infinite;
    }
    .floating .sphere {
      margin: 0;
      animation: none;
    }
    li {
      position: relative;
      margin-right: -4px;
      animation: float 2.8s ease-in-out infinite;
    }
    .who {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      padding: 0;
      border: 0;
      border-radius: 50%;
      background: none;
      cursor: pointer;
    }
    .who:focus-visible {
      outline: 2px solid var(--ww-white);
      outline-offset: 0;
    }
    /* o nome em cima da esfera: passar o mouse, focar ou tocar */
    .tip {
      position: absolute;
      bottom: calc(100% + 6px);
      left: 50%;
      padding: 4px 10px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-bg-dark);
      color: var(--ww-white);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
      transform: translateX(-50%);
      opacity: 0;
      pointer-events: none;
      transition: opacity var(--ww-fast) var(--ww-ease);
    }
    .who:hover + .tip,
    .who:focus-visible + .tip,
    .tip.open {
      opacity: 1;
    }
    .more {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      margin: 2px 0 0 4px;
      border-radius: 50%;
      background: var(--ww-bg-dark);
      font-size: 13px;
      font-weight: 700;
      animation: none;
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
    .label.reason {
      margin-left: 0;
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
      .sphere,
      li {
        animation: none;
      }
    }
  `,
})
export class LikedBy {
  readonly titleId = input.required<number>();
  /** texto do motivo da recomendação; só aparece quando nenhum amigo curtiu */
  readonly reason = input<string | null | undefined>(null);
  /** só as esferas, com o nome ao passar o mouse ou tocar (no reel) */
  readonly floating = input(false);
  private readonly likes = inject(LikesStore);
  protected readonly friends = computed<UserRef[]>(() => this.likes.friends(this.titleId()));
  /** no máximo 3 esferas; o texto (ou o "+N") conta o resto */
  protected readonly shown = computed(() => this.friends().slice(0, 3));
  protected readonly rest = computed(() => this.friends().length - this.shown().length);
  protected readonly label = computed(() => likedByLabel(this.friends()) ?? this.reason() ?? null);
  /** esfera com o nome aberto pelo toque */
  protected readonly open = signal<number | null>(null);
}
