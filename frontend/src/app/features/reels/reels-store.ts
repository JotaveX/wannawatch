import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CatalogApi } from '../../core/api/catalog-api';
import { TitleCard } from '../../core/api/models';

export const REELS_PAGE = 10;
/** Busca mais quando faltam estes reels para o fim. */
export const LOAD_AHEAD = 3;

/** Feed do Reels por cursor (rolagem infinita). */
@Injectable()
export class ReelsStore {
  private readonly api = inject(CatalogApi);
  private readonly destroyRef = inject(DestroyRef);

  /** undefined = ainda não buscou; null = acabou */
  private cursor: string | null | undefined = undefined;
  private fetching = false;

  readonly items = signal<TitleCard[]>([]);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  readonly loadingMore = signal(false);
  readonly done = signal(false);

  load(): void {
    if (this.fetching || this.cursor === null) return;
    this.fetching = true;
    const first = !this.items().length;
    if (first) this.status.set('loading');
    else this.loadingMore.set(true);

    this.api
      .reels({ cursor: this.cursor, size: REELS_PAGE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.fetching = false;
          this.loadingMore.set(false);
          this.cursor = page.nextCursor;
          this.done.set(page.nextCursor === null);
          const known = new Set(this.items().map((c) => c.id));
          this.items.update((xs) => [...xs, ...page.items.filter((c) => !known.has(c.id))]);
          this.status.set('ready');
        },
        error: () => {
          this.fetching = false;
          this.loadingMore.set(false);
          if (first) this.status.set('error');
        },
      });
  }

  /** Chamado quando o reel `index` aparece na tela. */
  reached(index: number): void {
    if (index >= this.items().length - LOAD_AHEAD) this.load();
  }
}
