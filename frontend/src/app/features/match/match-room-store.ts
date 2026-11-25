import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, filter, Subscription, switchMap, timer } from 'rxjs';
import { Room, RoomStatus, TitleCard } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';
import { LikesStore } from '../../core/social/likes-store';

/** De quanto em quanto tempo pergunta ao back se alguém entrou ou se deu match. */
export const POLL_MS = 2500;
export const DECK_PAGE = 20;
const PREFETCH_WHEN_LEFT = 5;

/** Ordem do ciclo da sala: uma resposta atrasada nunca faz a sala andar para trás. */
const STAGE: Record<RoomStatus, number> = { WAITING: 0, VOTING: 1, MATCHED: 2 };

/** "Bia entrou na sala" (key muda a cada aviso, para a tela reiniciar a animação) */
export interface RoomNotice {
  key: number;
  text: string;
}

export interface Vote {
  card: TitleCard;
  liked: boolean;
}

/**
 * Uma sala de Match aberta na tela: entra na sala, espera o dono começar (avisando quem entrou e
 * saiu), carrega o deck da sala (o mesmo para todos, sem o que eu já votei), manda os votos e
 * consulta a sala de tempos em tempos até dar match.
 */
@Injectable()
export class MatchRoomStore {
  private readonly api = inject(SocialApi);
  private readonly likes = inject(LikesStore);
  private readonly destroyRef = inject(DestroyRef);

  private code = '';
  /** undefined = ainda não buscou; null = acabou */
  private cursor: string | null | undefined = undefined;
  private fetching = false;
  private polling: Subscription | null = null;
  private noticeKey = 0;

  readonly room = signal<Room | null>(null);
  /** closed: a sala sumiu enquanto eu estava nela (o dono fechou); locked: já começou sem mim */
  readonly status = signal<'loading' | 'ready' | 'notfound' | 'locked' | 'closed' | 'error'>('loading');
  readonly deckStatus = signal<'loading' | 'ready' | 'error'>('loading');
  readonly cards = signal<TitleCard[]>([]);
  readonly index = signal(0);
  readonly history = signal<Vote[]>([]);
  /** aviso curto quando um voto não foi aceito */
  readonly problem = signal<string | null>(null);
  /** deu match com a sala aberta na tela (não vale para quem entra numa sala já decidida) */
  readonly freshMatch = signal(false);
  /** último "fulano entrou/saiu" (só de outras pessoas) */
  readonly notice = signal<RoomNotice | null>(null);
  readonly starting = signal(false);
  readonly startError = signal<string | null>(null);

  readonly current = computed(() => this.cards()[this.index()] ?? null);
  readonly next = computed(() => this.cards()[this.index() + 1] ?? null);
  readonly last = computed(() => this.history().at(-1) ?? null);
  readonly waiting = computed(() => this.room()?.status === 'WAITING');
  readonly matched = computed(() => this.room()?.status === 'MATCHED');
  readonly winner = computed(() => this.room()?.winner ?? null);
  readonly participants = computed(() => this.room()?.participants ?? []);
  readonly alone = computed(() => this.participants().length < 2);
  /** acabou o deck para mim e ainda não deu match */
  readonly finished = computed(
    () => this.deckStatus() === 'ready' && this.cursor === null && !this.current() && !this.matched(),
  );

  start(code: string): void {
    this.code = code.trim().toUpperCase();
    this.status.set('loading');
    this.api
      .joinRoom(this.code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (room) => {
          this.setRoom(room);
          this.status.set('ready');
          if (!this.matched()) this.startPolling();
        },
        error: (e: HttpErrorResponse) =>
          this.status.set(e.status === 404 ? 'notfound' : e.status === 409 ? 'locked' : 'error'),
      });
  }

  /** O dono começa a votação; todo mundo na sala de espera vê na próxima consulta. */
  startVoting(): void {
    if (this.starting() || !this.waiting()) return;
    this.starting.set(true);
    this.startError.set(null);
    this.api
      .startRoom(this.code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (room) => {
          this.starting.set(false);
          this.setRoom(room);
        },
        error: (e: HttpErrorResponse) => {
          this.starting.set(false);
          this.startError.set(
            e.status === 409 ? 'Chame pelo menos mais uma pessoa antes de começar.' : 'Não deu pra começar agora. Tente de novo.',
          );
        },
      });
  }

  /** Dono fechando a sala na sala de espera. */
  close(): ReturnType<SocialApi['closeRoom']> {
    this.stopPolling();
    return this.api.closeRoom(this.code);
  }

  vote(liked: boolean): void {
    const card = this.current();
    if (!card || this.matched()) return;
    this.problem.set(null);
    this.history.update((h) => [...h, { card, liked }]);
    this.index.update((i) => i + 1);
    this.prefetch();
    this.api
      .vote(this.code, card.id, liked)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (room) => this.setRoom(room),
        error: () => {
          // se ainda é o último voto, devolve a carta para votar de novo
          if (this.last()?.card.id === card.id) {
            this.history.update((h) => h.slice(0, -1));
            this.index.update((i) => Math.max(0, i - 1));
          }
          this.problem.set('Não deu pra registrar o voto. Tente de novo.');
        },
      });
  }

  undo(): void {
    const last = this.last();
    if (!last || this.matched()) return;
    this.history.update((h) => h.slice(0, -1));
    this.index.update((i) => Math.max(0, i - 1));
    this.api
      .unvote(this.code, last.card.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (room) => this.setRoom(room), error: () => undefined });
  }

  leave(): ReturnType<SocialApi['leaveRoom']> {
    this.stopPolling();
    return this.api.leaveRoom(this.code);
  }

  retryDeck(): void {
    this.fetchDeck();
  }

  /** A sala nunca volta uma etapa (resposta atrasada de uma consulta antiga). */
  private setRoom(room: Room): void {
    const prev = this.room();
    if (prev && STAGE[room.status] < STAGE[prev.status]) return;
    const wasOpen = this.status() === 'ready' && !this.matched();
    if (prev) this.announce(prev, room);
    this.room.set(room);
    // entrei numa sala votando ou o dono acabou de começar: hora de carregar o deck
    if (room.status === 'VOTING' && (!prev || prev.status === 'WAITING')) this.fetchDeck();
    if (room.status === 'MATCHED') {
      if (wasOpen) this.freshMatch.set(true);
      this.stopPolling();
      if (room.winner) this.likes.ensure([room.winner.id]);
    }
  }

  /** Avisa quem entrou ou saiu desde a última vez (eu mesmo não conto). */
  private announce(prev: Room, next: Room): void {
    const before = new Set(prev.participants.map((p) => p.user.id));
    const after = new Set(next.participants.map((p) => p.user.id));
    const joined = next.participants.filter((p) => !before.has(p.user.id)).map((p) => p.user.displayName);
    const left = prev.participants.filter((p) => !after.has(p.user.id)).map((p) => p.user.displayName);
    const text = joined.length
      ? `${joinNames(joined)} ${joined.length === 1 ? 'entrou' : 'entraram'} na sala`
      : left.length
        ? `${joinNames(left)} ${left.length === 1 ? 'saiu' : 'saíram'} da sala`
        : null;
    if (text) this.notice.set({ key: ++this.noticeKey, text });
  }

  private startPolling(): void {
    this.stopPolling();
    this.polling = timer(POLL_MS, POLL_MS)
      .pipe(
        // aba escondida não precisa ficar perguntando
        filter(() => typeof document === 'undefined' || !document.hidden),
        switchMap(() =>
          this.api.room(this.code).pipe(
            catchError((e: HttpErrorResponse) => {
              // a sala sumiu (o dono fechou) ou eu não estou mais nela
              if (e.status === 404) {
                this.stopPolling();
                this.status.set('closed');
              }
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((room) => this.setRoom(room));
  }

  private stopPolling(): void {
    this.polling?.unsubscribe();
    this.polling = null;
  }

  private prefetch(): void {
    if (this.cards().length - this.index() <= PREFETCH_WHEN_LEFT) this.fetchDeck();
  }

  private fetchDeck(): void {
    if (this.fetching || this.cursor === null) return;
    this.fetching = true;
    if (!this.cards().length) this.deckStatus.set('loading');
    this.api
      .roomDeck(this.code, this.cursor, DECK_PAGE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.fetching = false;
          this.cursor = page.nextCursor;
          const seen = new Set(this.cards().map((c) => c.id));
          const fresh = page.items.filter((c) => !seen.has(c.id));
          this.cards.update((cs) => [...cs, ...fresh]);
          this.likes.ensure(fresh.map((c) => c.id));
          this.deckStatus.set('ready');
        },
        error: () => {
          this.fetching = false;
          if (!this.current()) this.deckStatus.set('error');
        },
      });
  }
}

function joinNames(names: string[]): string {
  return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} e ${names.at(-1)}`;
}
