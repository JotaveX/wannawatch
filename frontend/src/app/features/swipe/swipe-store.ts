import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthStore } from '../../core/auth/auth-store';
import { CatalogApi } from '../../core/api/catalog-api';
import { SignalKind, TitleCard } from '../../core/api/models';
import { LikesStore } from '../../core/social/likes-store';
import { SignalsStore } from '../../core/social/signals-store';

export type Decision = 'pass' | 'maybe' | 'like' | 'seen';

/** "Quero ver" vai para as curtidas; o resto vira um sinal no back. */
const SIGNAL: Record<Exclude<Decision, 'like'>, SignalKind> = {
  pass: 'PASS',
  maybe: 'MAYBE',
  seen: 'SEEN',
};

export interface Choice {
  card: TitleCard;
  decision: Decision;
}

/** Quantos títulos buscar por vez e quando buscar mais. */
export const PAGE_SIZE = 20;
export const PREFETCH_WHEN_LEFT = 5;

/** Ordem aleatória, mas fixa durante a visita (o back ordena por hash da seed). */
export function newSeed(): string {
  return Math.random().toString(36).slice(2, 12);
}

/** Onde o deck que falta fica guardado, para o F5 voltar na mesma carta (um por usuário). */
export const DECK_STORAGE_KEY = 'ww.swipe';

function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

/**
 * Estado do Swipe: deck carregado aos poucos por cursor, posição atual e histórico de escolhas.
 * Toda escolha vai para o back: "quero ver" nas curtidas (aparece para os amigos) e
 * talvez/passa/já vi como sinais. "Voltar" desfaz a escolha no back também.
 *
 * As cartas que ainda faltam ficam no localStorage: ao recarregar, o deck volta na mesma carta e
 * o resto vem de uma seed nova (o back já tira o que foi avaliado, e as repetidas são ignoradas).
 */
@Injectable()
export class SwipeStore {
  private readonly api = inject(CatalogApi);
  private readonly likes = inject(LikesStore);
  private readonly signals = inject(SignalsStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly storageKey = `${DECK_STORAGE_KEY}.${inject(AuthStore).user()?.id ?? 'anon'}`;

  private seed = newSeed();
  /** undefined = ainda não buscou; null = acabou */
  private cursor: string | null | undefined = undefined;
  private fetching = false;

  readonly cards = signal<TitleCard[]>([]);
  readonly index = signal(0);
  readonly history = signal<Choice[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');

  readonly current = computed(() => this.cards()[this.index()] ?? null);
  readonly next = computed(() => this.cards()[this.index() + 1] ?? null);
  readonly last = computed(() => this.history().at(-1) ?? null);
  readonly finished = computed(
    () => this.status() === 'ready' && this.cursor === null && !this.current(),
  );
  readonly counts = computed(() => {
    const c: Record<Decision, number> = { pass: 0, maybe: 0, like: 0, seen: 0 };
    for (const h of this.history()) c[h.decision]++;
    return c;
  });
  readonly liked = computed(() =>
    this.history()
      .filter((h) => h.decision === 'like')
      .map((h) => h.card),
  );

  start(): void {
    const saved = this.restore();
    if (saved.length) {
      this.cards.set(saved);
      this.likes.ensure(saved.map((c) => c.id));
      this.status.set('ready');
      this.prefetch();
    } else {
      this.fetch();
    }
  }

  decide(decision: Decision): void {
    const card = this.current();
    if (!card) return;
    this.history.update((h) => [...h, { card, decision }]);
    this.index.update((i) => i + 1);
    this.persist(card.id, decision, true);
    this.save();
    this.prefetch();
  }

  undo(): void {
    const last = this.last();
    if (!last) return;
    this.history.update((h) => h.slice(0, -1));
    this.index.update((i) => Math.max(0, i - 1));
    this.persist(last.card.id, last.decision, false);
    this.save();
  }

  /** Guarda as cartas que faltam (a de cima primeiro). */
  private save(): void {
    const rest = this.cards().slice(this.index(), this.index() + PAGE_SIZE);
    try {
      if (rest.length) storage()?.setItem(this.storageKey, JSON.stringify(rest));
      else storage()?.removeItem(this.storageKey);
    } catch {
      // sem espaço ou bloqueado: só não lembra a posição
    }
  }

  private restore(): TitleCard[] {
    try {
      const parsed: unknown = JSON.parse(storage()?.getItem(this.storageKey) ?? '[]');
      if (!Array.isArray(parsed)) return [];
      return parsed.filter(
        (c): c is TitleCard => typeof c?.id === 'number' && typeof c?.title === 'string',
      );
    } catch {
      return [];
    }
  }

  private persist(titleId: number, decision: Decision, on: boolean): void {
    if (decision === 'like') this.likes.setLiked(titleId, on, 'SWIPE');
    else this.signals.set(titleId, SIGNAL[decision], on, 'SWIPE');
  }

  /** Recomeça com outra ordem (quando o deck acaba). */
  reshuffle(): void {
    this.seed = newSeed();
    this.cursor = undefined;
    this.cards.set([]);
    this.index.set(0);
    this.history.set([]);
    this.fetch();
  }

  retry(): void {
    this.fetch();
  }

  private prefetch(): void {
    if (this.cards().length - this.index() <= PREFETCH_WHEN_LEFT) this.fetch();
  }

  private fetch(): void {
    if (this.fetching || this.cursor === null) return;
    this.fetching = true;
    if (!this.cards().length) this.status.set('loading');
    this.api
      .swipeDeck({ seed: this.seed, cursor: this.cursor, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.fetching = false;
          this.cursor = page.nextCursor;
          const seen = new Set(this.cards().map((c) => c.id));
          const fresh = page.items.filter((c) => !seen.has(c.id));
          this.cards.update((cs) => [...cs, ...fresh]);
          this.likes.ensure(fresh.map((c) => c.id));
          this.status.set('ready');
          this.save();
        },
        error: () => {
          this.fetching = false;
          // sem cartas na tela, mostra erro; com cartas, tenta de novo na próxima escolha
          if (!this.current()) this.status.set('error');
        },
      });
  }
}
