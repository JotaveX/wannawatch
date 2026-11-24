import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CatalogApi } from '../../core/api/catalog-api';
import { TitleCard } from '../../core/api/models';
import { SocialMock } from '../../mocks/social.mock';

export type Decision = 'pass' | 'maybe' | 'like' | 'seen';

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

/**
 * Estado do Swipe: deck carregado aos poucos por cursor, posição atual e histórico de escolhas.
 * As escolhas ficam só na memória desta tela (persistir está fora do escopo por enquanto).
 */
@Injectable()
export class SwipeStore {
  private readonly api = inject(CatalogApi);
  private readonly social = inject(SocialMock);
  private readonly destroyRef = inject(DestroyRef);

  private seed = newSeed();
  /** undefined = ainda não buscou; null = acabou */
  private cursor: string | null | undefined = undefined;
  private fetching = false;

  readonly cards = signal<TitleCard[]>([]);
  readonly index = signal(0);
  readonly history = signal<Choice[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  /** título que "deu match" no grupo na última escolha (mock) */
  readonly match = signal<TitleCard | null>(null);

  readonly current = computed(() => this.cards()[this.index()] ?? null);
  readonly next = computed(() => this.cards()[this.index() + 1] ?? null);
  readonly last = computed(() => this.history().at(-1) ?? null);
  readonly finished = computed(() => this.status() === 'ready' && this.cursor === null && !this.current());
  readonly counts = computed(() => {
    const c: Record<Decision, number> = { pass: 0, maybe: 0, like: 0, seen: 0 };
    for (const h of this.history()) c[h.decision]++;
    return c;
  });
  readonly liked = computed(() => this.history().filter((h) => h.decision === 'like').map((h) => h.card));

  start(): void {
    this.fetch();
  }

  decide(decision: Decision): void {
    const card = this.current();
    if (!card) return;
    this.history.update((h) => [...h, { card, decision }]);
    this.index.update((i) => i + 1);
    const everyone = this.social.friendsWhoWant(card.id) === this.social.group.members;
    this.match.set(decision === 'like' && this.social.isGroup() && everyone ? card : null);
    this.prefetch();
  }

  undo(): void {
    if (!this.history().length) return;
    this.history.update((h) => h.slice(0, -1));
    this.index.update((i) => Math.max(0, i - 1));
    this.match.set(null);
  }

  /** Recomeça com outra ordem (quando o deck acaba). */
  reshuffle(): void {
    this.seed = newSeed();
    this.cursor = undefined;
    this.cards.set([]);
    this.index.set(0);
    this.history.set([]);
    this.match.set(null);
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
          this.cards.update((cs) => [...cs, ...page.items.filter((c) => !seen.has(c.id))]);
          this.status.set('ready');
        },
        error: () => {
          this.fetching = false;
          // sem cartas na tela, mostra erro; com cartas, tenta de novo na próxima escolha
          if (!this.current()) this.status.set('error');
        },
      });
  }
}
