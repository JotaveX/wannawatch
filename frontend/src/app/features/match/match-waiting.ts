import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, output, signal } from '@angular/core';
import { Room } from '../../core/api/models';
import { Avatar } from '../../shared/ui/avatar';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { RoomNotice } from './match-room-store';
import { peopleCount, roomFilters, roomName } from './match-format';
import { roomMascot } from '../../shared/ui/mascots';

/** Quanto tempo o "fulano entrou" fica na tela. */
export const NOTICE_MS = 3200;

/**
 * Sala de espera do Match: o mascote da sala esperando, quem já chegou e quem está faltando
 * começar. O dono começa (com 2 ou mais pessoas) ou fecha a sala; os outros esperam ou saem.
 */
@Component({
  selector: 'ww-match-waiting',
  imports: [Avatar, Button, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="stage" [style.--glow]="mascot().color" aria-labelledby="espera-titulo">
      <div class="notice-slot" aria-live="polite">
        <!-- @for com track pela key: cada aviso novo é um elemento novo e a animação recomeça -->
        @for (n of notices(); track n.key) {
          <p class="notice">
            <span class="wave" aria-hidden="true">👋</span>{{ n.text }}
          </p>
        }
      </div>

      <div class="hero">
        <div class="mascot-wrap" aria-hidden="true">
          <span class="ring r1"></span>
          <span class="ring r2"></span>
          <span class="ring r3"></span>
          <img class="mascot" [src]="mascot().src" alt="" width="176" height="176" />
        </div>

        <p class="kicker">Sala de espera · <span class="code">{{ room().code }}</span></p>
        <h1 id="espera-titulo">{{ title() }}</h1>
        <p class="sub">
          <strong>{{ name() }}</strong> · {{ filters() }}
        </p>
      </div>

      <ul class="people" aria-label="Quem está na sala">
        @for (p of room().participants; track p.user.id) {
          <li class="person">
            <span class="face">
              <ww-avatar [user]="p.user" [size]="56" />
              @if (p.user.id === room().owner.id) {
                <span class="crown" title="Dono da sala"><ww-icon name="star" [size]="12" [stroke]="2.6" /></span>
              }
            </span>
            <span class="who">{{ p.user.id === meId() ? 'Você' : p.user.displayName }}</span>
          </li>
        }
        <li class="person seat">
          <button type="button" class="seat-btn" (click)="invite.emit()" aria-label="Convidar mais gente">
            <span aria-hidden="true">+</span>
          </button>
          <span class="who">Convidar</span>
        </li>
      </ul>
      <p class="count" role="status">{{ countText() }}</p>

      <div class="footer">
        @if (isOwner()) {
          <button wwButton variant="primary" size="lg" class="go" (click)="start.emit()"
                  [disabled]="!canStart() || starting()">
            {{ starting() ? 'Começando…' : 'Começar' }}
          </button>
          @if (startError(); as error) {
            <p class="error" role="alert">{{ error }}</p>
          } @else if (!canStart()) {
            <p class="hint">Precisa de pelo menos 2 pessoas pra começar.</p>
          }
        } @else {
          <p class="waiting-for">
            Esperando <strong>{{ room().owner.displayName }}</strong> começar<span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>
          </p>
        }
        <div class="secondary">
          <button wwButton variant="soft" size="sm" (click)="invite.emit()">
            <ww-icon name="send" [size]="16" [stroke]="2.2" />Convidar
          </button>
          @if (isOwner()) {
            <button class="text-btn" type="button" (click)="close.emit()" [disabled]="busy()">Fechar sala</button>
          } @else {
            <button class="text-btn" type="button" (click)="leave.emit()" [disabled]="busy()">Sair da sala</button>
          }
        </div>
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .stage {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--ww-space-6);
      min-height: calc(100dvh - var(--ww-tabbar-h) - env(safe-area-inset-bottom) - 12px);
      padding: var(--ww-space-14) var(--ww-page-x) var(--ww-space-8);
      overflow: hidden;
      text-align: center;
      isolation: isolate;
    }
    /* brilho suave da cor do mascote, atrás de tudo */
    .stage::before {
      content: '';
      position: absolute;
      inset: -20% -10% auto;
      height: 70%;
      z-index: -1;
      background: radial-gradient(closest-side, color-mix(in srgb, var(--glow) 28%, transparent), transparent);
      filter: blur(20px);
      animation: glow 6s ease-in-out infinite alternate;
    }

    .notice-slot {
      position: absolute;
      top: var(--ww-space-6);
      left: 0;
      right: 0;
      display: flex;
      justify-content: center;
      pointer-events: none;
    }
    .notice {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 18px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-graphite);
      color: var(--ww-white);
      font-size: 14px;
      font-weight: 600;
      box-shadow: var(--ww-shadow-lift);
      animation: notice-in var(--ww-slow) var(--ww-ease-spring) both;
    }
    .wave {
      display: inline-block;
      transform-origin: 70% 70%;
      animation: wave 1.2s ease-in-out 2;
    }

    .hero {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--ww-space-2);
    }
    .mascot-wrap {
      position: relative;
      display: grid;
      place-items: center;
      width: 176px;
      height: 176px;
      margin-bottom: var(--ww-space-10);
    }
    .mascot {
      position: relative;
      width: 176px;
      height: 176px;
      border-radius: 44px;
      box-shadow: 0 24px 48px -16px color-mix(in srgb, var(--glow) 70%, transparent);
      animation: float 3.6s ease-in-out infinite;
    }
    /* ondas saindo do mascote: "chamando a galera" */
    .ring {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      border: 2px solid color-mix(in srgb, var(--glow) 45%, transparent);
      animation: ring 3.6s var(--ww-ease-out) infinite;
      opacity: 0;
    }
    .r2 {
      animation-delay: 1.2s;
    }
    .r3 {
      animation-delay: 2.4s;
    }

    .kicker {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--ww-gray-text);
    }
    .code {
      font-family: var(--ww-font-display);
      color: var(--ww-graphite);
      letter-spacing: 0.12em;
    }
    h1 {
      font-family: var(--ww-font-display);
      font-size: clamp(30px, 5vw, 44px);
      font-weight: 800;
      letter-spacing: var(--ww-tracking-display);
      line-height: 1.05;
    }
    .sub {
      color: var(--ww-gray-text);
      font-size: 15px;
    }
    .sub strong {
      color: var(--ww-graphite);
    }

    .people {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--ww-space-4);
      max-width: 640px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .person {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      width: 72px;
      animation: pop-in var(--ww-slow) var(--ww-ease-spring) both;
    }
    .face {
      position: relative;
      display: grid;
      place-items: center;
      padding: 3px;
      border-radius: 50%;
      background: var(--ww-white);
      box-shadow: var(--ww-shadow-soft);
    }
    .crown {
      position: absolute;
      right: -2px;
      bottom: -2px;
      display: grid;
      place-items: center;
      width: 22px;
      height: 22px;
      border: 2px solid var(--ww-white);
      border-radius: 50%;
      background: var(--ww-yellow);
      color: var(--ww-graphite);
    }
    .who {
      max-width: 100%;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      font-size: 13px;
      font-weight: 600;
    }
    .seat .who {
      color: var(--ww-gray-text);
    }
    .seat-btn {
      display: grid;
      place-items: center;
      width: 62px;
      height: 62px;
      border: 2px dashed var(--ww-border);
      border-radius: 50%;
      background: transparent;
      color: var(--ww-gray-text);
      font-size: 26px;
      font-weight: 300;
      cursor: pointer;
      transition:
        border-color var(--ww-medium) var(--ww-ease-out),
        color var(--ww-medium) var(--ww-ease-out),
        transform var(--ww-medium) var(--ww-ease-spring);
    }
    @media (hover: hover) and (pointer: fine) {
      .seat-btn:hover {
        border-color: var(--glow);
        color: var(--ww-graphite);
        transform: scale(1.05);
      }
    }
    .seat-btn:focus-visible {
      outline: 2px solid var(--focus-ring);
      outline-offset: 2px;
    }
    .count {
      margin-top: calc(var(--ww-space-2) * -1);
      color: var(--ww-gray-text);
      font-size: 14px;
    }

    .footer {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--ww-space-3);
      margin-top: var(--ww-space-2);
    }
    .go {
      min-width: 220px;
    }
    .hint,
    .error {
      font-size: 13px;
      color: var(--ww-gray-text);
    }
    .error {
      color: var(--ww-pink-strong);
      font-weight: 600;
    }
    .waiting-for {
      display: inline-flex;
      align-items: baseline;
      padding: 12px 22px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-white);
      box-shadow: var(--ww-shadow-soft);
      font-size: 15px;
    }
    .waiting-for strong {
      margin: 0 0.3em;
    }
    .dots {
      display: inline-flex;
      gap: 3px;
      margin-left: 4px;
    }
    .dots i {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: currentColor;
      animation: dot 1.2s ease-in-out infinite;
    }
    .dots i:nth-child(2) {
      animation-delay: 0.15s;
    }
    .dots i:nth-child(3) {
      animation-delay: 0.3s;
    }
    .secondary {
      display: flex;
      align-items: center;
      gap: var(--ww-space-2);
    }
    .text-btn {
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-3);
      border: 0;
      background: none;
      color: var(--ww-gray-text);
      font: inherit;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }
    .text-btn:hover {
      color: var(--ww-pink-strong);
    }
    .text-btn:focus-visible {
      outline: 2px solid var(--focus-ring);
      border-radius: var(--ww-radius-sm);
    }

    @keyframes float {
      0%,
      100% {
        transform: translateY(0) rotate(-1.5deg);
      }
      50% {
        transform: translateY(-12px) rotate(1.5deg);
      }
    }
    @keyframes ring {
      0% {
        transform: scale(0.9);
        opacity: 0.9;
      }
      100% {
        transform: scale(1.6);
        opacity: 0;
      }
    }
    @keyframes glow {
      from {
        transform: scale(0.95);
        opacity: 0.8;
      }
      to {
        transform: scale(1.08);
        opacity: 1;
      }
    }
    @keyframes pop-in {
      from {
        transform: scale(0.4) translateY(12px);
        opacity: 0;
      }
    }
    @keyframes notice-in {
      from {
        transform: translateY(-16px) scale(0.9);
        opacity: 0;
      }
    }
    @keyframes wave {
      0%,
      100% {
        transform: rotate(0);
      }
      25% {
        transform: rotate(18deg);
      }
      75% {
        transform: rotate(-10deg);
      }
    }
    @keyframes dot {
      0%,
      80%,
      100% {
        transform: translateY(0);
        opacity: 0.35;
      }
      40% {
        transform: translateY(-4px);
        opacity: 1;
      }
    }
  `,
})
export class MatchWaiting {
  readonly room = input.required<Room>();
  readonly meId = input<number | undefined>();
  readonly notice = input<RoomNotice | null>(null);
  readonly starting = input(false);
  readonly startError = input<string | null>(null);
  /** saindo ou fechando: trava os botões */
  readonly busy = input(false);

  readonly start = output<void>();
  readonly invite = output<void>();
  readonly leave = output<void>();
  readonly close = output<void>();

  protected readonly mascot = computed(() => roomMascot(this.room().mascot));
  protected readonly name = computed(() => roomName(this.room()));
  protected readonly filters = computed(() => roomFilters(this.room()));
  protected readonly isOwner = computed(() => this.room().owner.id === this.meId());
  protected readonly canStart = computed(() => this.room().participants.length >= 2);
  protected readonly title = computed(() =>
    this.room().participants.length < 2 ? 'Esperando a galera' : 'A galera está chegando',
  );
  protected readonly countText = computed(() => {
    const n = this.room().participants.length;
    return n < 2 ? 'Só você por aqui. Chama alguém!' : `${peopleCount(n)} na sala`;
  });

  /** o aviso some sozinho depois de alguns segundos */
  protected readonly shownNotice = signal<RoomNotice | null>(null);
  protected readonly notices = computed(() => {
    const n = this.shownNotice();
    return n ? [n] : [];
  });
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const n = this.notice();
      if (!n) return;
      this.shownNotice.set(n);
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.shownNotice.set(null), NOTICE_MS);
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }
}
