import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, EMPTY, filter, switchMap, timer } from 'rxjs';
import { Room } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { MatchCreateDialog } from './match-create-dialog';
import { peopleCount, roomFilters, roomName } from './match-format';
import { roomMascot } from '../../shared/ui/mascots';

/** De quanto em quanto tempo a lista de salas abertas se atualiza. */
export const LOBBY_POLL_MS = 4000;

/**
 * /match: as salas abertas (esperando gente, de todo mundo, e as que eu estou votando) e o botão
 * de criar a minha. Quem cria cai na sala de espera.
 */
@Component({
  selector: 'ww-match-lobby-page',
  imports: [Button, Icon, MatchCreateDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page ww-stagger">
      <header class="head">
        <div class="titles">
          <p class="kicker">{{ greeting() }}</p>
          <h1>Match</h1>
          <p class="lead">
            Salas abertas agora. Entre numa, espere o dono começar e todo mundo vota no mesmo deck: o primeiro
            título que todos quiserem ver é o da noite.
          </p>
        </div>
        <button wwButton variant="primary" class="create-top" (click)="openModal()">
          <ww-icon name="plus" [size]="18" [stroke]="2.4" />Criar sala
        </button>
      </header>

      <section class="board" aria-labelledby="salas-abertas">
        <h2 id="salas-abertas" class="visually-hidden">Salas abertas</h2>
        @switch (rooms().status) {
          @case ('loading') {
            <p class="muted" aria-busy="true">Procurando salas…</p>
          }
          @case ('error') {
            <p class="muted" role="alert">Não deu pra carregar as salas. Confira se o back-end está rodando.</p>
          }
          @default {
            <ul class="bubbles">
              @for (room of rooms().items; track room.code) {
                <li class="bubble-item">
                  <button type="button" class="bubble" (click)="enter(room)"
                          [attr.aria-label]="bubbleLabel(room)">
                    <img class="bubble-mascot" [src]="mascot(room)" alt="" width="76" height="76" />
                    <strong class="bubble-name">{{ name(room) }}</strong>
                    <span class="bubble-sub">{{ filters(room) }}</span>
                    <span class="bubble-meta">
                      <ww-icon name="groups" [size]="14" [stroke]="2.2" />{{ people(room) }}
                    </span>
                    @if (room.status === 'VOTING') {
                      <span class="tag voting">Votando</span>
                    } @else if (isMine(room)) {
                      <span class="tag">Você está aqui</span>
                    }
                  </button>
                  @if (canClose(room)) {
                    <button type="button" class="close" (click)="close(room)" [disabled]="closing() === room.code"
                            [attr.aria-label]="'Fechar a sala de ' + name(room)">
                      <ww-icon name="x" [size]="14" [stroke]="2.6" />
                    </button>
                  }
                </li>
              }
              <li class="bubble-item">
                <button type="button" class="bubble new" (click)="openModal()">
                  <span class="plus" aria-hidden="true"><ww-icon name="plus" [size]="22" [stroke]="2" /></span>
                  <strong class="bubble-name">Crie a sua sala</strong>
                  <span class="bubble-sub">e chame a galera</span>
                </button>
              </li>
            </ul>
          }
        }
        @if (problem(); as text) {
          <p class="problem" role="alert">{{ text }}</p>
        }
      </section>
    </div>

    <ww-match-create-dialog />
  `,
  styles: `
    :host {
      display: block;
    }
    .page {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-6);
      min-height: calc(100dvh - var(--ww-tabbar-h) - env(safe-area-inset-bottom));
      padding: var(--ww-space-8) var(--ww-page-x);
    }
    .head {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--ww-space-4);
      flex-wrap: wrap;
    }
    .kicker {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-2);
    }
    h1 {
      font-size: 40px;
      font-weight: 800;
      letter-spacing: var(--ww-tracking-display);
    }
    .lead {
      max-width: 560px;
      margin-top: var(--ww-space-2);
      color: var(--text-2);
    }
    .create-top {
      flex-shrink: 0;
    }

    /* quadro das salas: ocupa todo o resto da tela */
    .board {
      position: relative;
      flex-grow: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 420px;
      padding: var(--ww-space-10) var(--ww-space-6);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-xl);
      background:
        radial-gradient(circle, color-mix(in srgb, var(--text) 9%, transparent) 1px, transparent 1.5px) 0 0 / 22px 22px,
        var(--surface);
    }
    .bubbles {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: var(--ww-space-10) var(--ww-space-8);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .bubble-item {
      position: relative;
      animation: bubble-in var(--ww-slow) var(--ww-ease-spring) both;
    }
    .bubble {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 2px;
      width: 196px;
      height: 196px;
      padding: var(--ww-space-4);
      border: var(--ww-hairline) solid var(--border);
      border-radius: 50%;
      background: var(--bg);
      color: inherit;
      font: inherit;
      text-align: center;
      cursor: pointer;
      box-shadow:
        var(--ww-shadow-soft),
        0 0 0 10px color-mix(in srgb, var(--bg) 55%, transparent);
      transition:
        transform var(--ww-medium) var(--ww-ease-spring),
        box-shadow var(--ww-medium) var(--ww-ease-out);
    }
    @media (hover: hover) and (pointer: fine) {
      .bubble:hover {
        transform: translateY(-4px) scale(1.03);
        box-shadow:
          var(--ww-shadow-lift),
          0 0 0 10px color-mix(in srgb, var(--bg) 55%, transparent);
      }
      .bubble:hover .bubble-mascot {
        transform: rotate(-4deg) scale(1.06);
      }
    }
    .bubble:active {
      transform: scale(0.97);
    }
    .bubble:focus-visible {
      outline: 3px solid var(--focus-ring);
      outline-offset: 4px;
    }
    .bubble-mascot {
      width: 76px;
      height: 76px;
      margin-bottom: 6px;
      border-radius: 22px;
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    .bubble-name {
      max-width: 150px;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      font-size: 16px;
      font-weight: 700;
    }
    .bubble-sub {
      max-width: 150px;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      color: var(--text-2);
      font-size: 13px;
    }
    .bubble-meta {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-top: 2px;
      color: var(--text-2);
      font-size: 12px;
      font-weight: 600;
    }
    .tag {
      position: absolute;
      bottom: -10px;
      left: 50%;
      transform: translateX(-50%);
      padding: 4px 10px;
      border-radius: var(--ww-radius-pill);
      background: var(--surface-2);
      color: var(--text);
      font-size: 11px;
      font-weight: 700;
      white-space: nowrap;
    }
    .tag.voting {
      background: var(--ww-yellow);
      color: var(--ww-bg-dark);
    }
    .close {
      position: absolute;
      top: 6px;
      right: 6px;
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border: var(--ww-hairline) solid var(--border);
      border-radius: 50%;
      background: var(--surface);
      color: var(--text-2);
      cursor: pointer;
      box-shadow: var(--ww-shadow-soft);
      transition:
        color var(--ww-fast) var(--ww-ease-out),
        transform var(--ww-medium) var(--ww-ease-spring);
    }
    .close:hover {
      color: var(--ww-pink-strong);
      transform: scale(1.1);
    }
    .close:focus-visible {
      outline: 2px solid var(--focus-ring);
      outline-offset: 2px;
    }
    .bubble.new {
      border: 2px dashed var(--border);
      background: transparent;
      box-shadow: none;
    }
    .plus {
      display: grid;
      place-items: center;
      width: 48px;
      height: 48px;
      margin-bottom: 8px;
      border-radius: 50%;
      background: var(--surface-2);
      color: var(--text-2);
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    @media (hover: hover) and (pointer: fine) {
      .bubble.new:hover {
        border-color: var(--text-2);
        box-shadow: none;
      }
      .bubble.new:hover .plus {
        transform: rotate(90deg);
      }
    }
    .bubble.new .bubble-name {
      font-size: 15px;
    }
    .muted {
      color: var(--text-2);
    }
    .problem {
      position: absolute;
      bottom: var(--ww-space-4);
      left: 50%;
      transform: translateX(-50%);
      padding: 8px 14px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-graphite);
      color: var(--ww-white);
      font-size: 14px;
      font-weight: 600;
      white-space: nowrap;
    }
    @keyframes bubble-in {
      from {
        transform: scale(0.6);
        opacity: 0;
      }
    }

    @media (max-width: 599px) {
      h1 {
        font-size: 32px;
      }
      .board {
        padding: var(--ww-space-8) var(--ww-space-3);
        border-radius: var(--ww-radius-lg);
      }
      .bubbles {
        gap: var(--ww-space-8) var(--ww-space-4);
      }
      .bubble {
        width: 150px;
        height: 150px;
        padding: var(--ww-space-3);
        box-shadow:
          var(--ww-shadow-soft),
          0 0 0 6px color-mix(in srgb, var(--bg) 55%, transparent);
      }
      .bubble-mascot {
        width: 56px;
        height: 56px;
        border-radius: 16px;
        margin-bottom: 4px;
      }
      .bubble-name,
      .bubble-sub {
        max-width: 118px;
      }
      .bubble-name {
        font-size: 14px;
      }
      .bubble-sub {
        font-size: 12px;
      }
    }
  `,
})
export class MatchLobbyPage {
  private readonly social = inject(SocialApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly createDialog = viewChild.required(MatchCreateDialog);

  protected readonly closing = signal<string | null>(null);
  protected readonly problem = signal<string | null>(null);
  protected readonly rooms = signal<{ status: 'loading' | 'error' | 'ok'; items: Room[] }>({ status: 'loading', items: [] });

  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    const hello = hour < 5 ? 'Boa noite' : hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    const name = this.auth.user()?.displayName;
    return name ? `${hello}, ${name}` : hello;
  });

  private problemTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    // a lista se atualiza sozinha: salas novas aparecem e as que começaram somem
    timer(0, LOBBY_POLL_MS)
      .pipe(
        filter(() => typeof document === 'undefined' || !document.hidden),
        switchMap(() =>
          this.social.openRooms().pipe(
            catchError(() => {
              // se já tinha lista, mantém a última; só mostra erro se nunca carregou
              if (this.rooms().status !== 'ok') this.rooms.set({ status: 'error', items: [] });
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((items) => {
        const closing = this.closing();
        this.rooms.set({ status: 'ok', items: closing ? items.filter((r) => r.code !== closing) : items });
      });

    this.destroyRef.onDestroy(() => clearTimeout(this.problemTimer));
  }

  protected openModal(): void {
    this.createDialog().open();
  }

  protected enter(room: Room): void {
    this.router.navigate(['/match', room.code]);
  }

  /** O X da bolha: o dono fecha a sala que ainda espera gente. */
  protected close(room: Room): void {
    if (this.closing()) return;
    this.closing.set(room.code);
    this.rooms.update((r) => ({ ...r, items: r.items.filter((i) => i.code !== room.code) }));
    this.social
      .closeRoom(room.code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.closing.set(null),
        error: () => {
          this.closing.set(null);
          // devolve a bolha; a próxima consulta acerta a ordem
          this.rooms.update((r) => ({ ...r, items: [room, ...r.items.filter((i) => i.code !== room.code)] }));
          this.flash('Não deu pra fechar a sala agora.');
        },
      });
  }

  protected isMine(room: Room): boolean {
    const me = this.auth.user()?.id;
    return me !== undefined && room.participants.some((p) => p.user.id === me);
  }

  protected canClose(room: Room): boolean {
    return room.status === 'WAITING' && room.owner.id === this.auth.user()?.id;
  }

  protected mascot(room: Room): string {
    return roomMascot(room.mascot).src;
  }

  protected name(room: Room): string {
    return roomName(room);
  }

  protected filters(room: Room): string {
    return roomFilters(room);
  }

  protected people(room: Room): string {
    return peopleCount(room.participants.length);
  }

  protected bubbleLabel(room: Room): string {
    const state = room.status === 'VOTING' ? 'votando' : 'esperando gente';
    return `Sala de ${roomName(room)}, ${roomFilters(room)}, ${peopleCount(room.participants.length)}, ${state}`;
  }

  private flash(text: string): void {
    this.problem.set(text);
    clearTimeout(this.problemTimer);
    this.problemTimer = setTimeout(() => this.problem.set(null), 2600);
  }
}
