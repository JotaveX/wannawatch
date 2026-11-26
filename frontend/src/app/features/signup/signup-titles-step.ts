import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  Observable,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { MediaType, Page, TitleCard } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Poster } from '../../shared/ui/poster';
import { Segmented, SegmentOption } from '../../shared/ui/segmented';
import { problemDetail } from './signup-errors';

/** Mesmos limites do SignupService no back. */
export const MIN_TITLES = 5;
export const MAX_TITLES = 50;
const PAGE_SIZE = 24;

/**
 * Etapa 2: pelo menos 5 títulos de que a pessoa gosta (viram "quero ver" e já personalizam o app)
 * e, opcionalmente, um favorito de todos os tempos, escolhido entre eles pela estrela.
 */
@Component({
  selector: 'ww-signup-titles-step',
  imports: [Button, Icon, Poster, Segmented],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signup-titles-step.html',
  styleUrl: './signup-titles-step.css',
})
export class SignupTitlesStep {
  private readonly catalog = inject(CatalogApi);
  private readonly api = inject(SocialApi);
  private readonly auth = inject(AuthStore);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  protected readonly min = MIN_TITLES;
  protected readonly kinds: SegmentOption<MediaType>[] = [
    { value: 'MOVIE', label: 'Filmes' },
    { value: 'SERIES', label: 'Séries' },
  ];
  protected readonly kind = signal<MediaType>('MOVIE');
  protected readonly query = signal('');

  /** o que aparece na grade (populares ou resultado da busca) */
  protected readonly items = signal<TitleCard[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly hasMore = signal(false);
  private page = 0;

  /** escolhidos, na ordem em que foram marcados */
  protected readonly picked = signal<TitleCard[]>([]);
  protected readonly favoriteId = signal<number | null>(null);
  protected readonly count = computed(() => this.picked().length);
  protected readonly missing = computed(() => Math.max(0, MIN_TITLES - this.count()));
  protected readonly progress = computed(() => Math.min(1, this.count() / MIN_TITLES));
  private readonly pickedIds = computed(() => new Set(this.picked().map((t) => t.id)));

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Busca de verdade só a partir de 2 letras; antes disso, os populares. */
  private readonly term = computed(() => {
    const q = this.query().trim();
    return q.length >= 2 ? q : '';
  });
  protected readonly searching = computed(() => !!this.term());

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
    toObservable(computed(() => ({ q: this.term(), kind: this.kind() })))
      .pipe(
        debounceTime(250),
        distinctUntilChanged((a, b) => a.q === b.q && a.kind === b.kind),
        tap(() => {
          this.loading.set(true);
          this.failed.set(false);
        }),
        switchMap(({ q, kind }) => this.fetch(q, kind, 0).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.show(page, false));
  }

  protected isPicked(id: number): boolean {
    return this.pickedIds().has(id);
  }

  protected toggle(title: TitleCard): void {
    this.error.set(null);
    if (this.isPicked(title.id)) {
      this.picked.update((list) => list.filter((t) => t.id !== title.id));
      if (this.favoriteId() === title.id) this.favoriteId.set(null);
      return;
    }
    if (this.count() >= MAX_TITLES) {
      this.error.set(`Dá para escolher até ${MAX_TITLES}. Tire algum para trocar.`);
      return;
    }
    this.picked.update((list) => [...list, title]);
  }

  /** Só um favorito: marcar outro troca; marcar o mesmo de novo tira. */
  protected toggleFavorite(title: TitleCard): void {
    this.favoriteId.update((id) => (id === title.id ? null : title.id));
  }

  protected loadMore(): void {
    if (this.loading() || !this.hasMore()) return;
    this.loading.set(true);
    this.fetch(this.term(), this.kind(), this.page + 1)
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.show(page, true));
  }

  protected submit(): void {
    if (this.busy()) return;
    if (this.missing() > 0) {
      this.error.set(`Escolha mais ${this.missing()} para continuar.`);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .pickTitles(
        this.picked().map((t) => t.id),
        this.favoriteId(),
      )
      .subscribe({
        // a página segue para a confirmação do e-mail
        next: (account) => this.auth.updateAccount(account),
        error: (e: HttpErrorResponse) => {
          this.busy.set(false);
          this.error.set(
            problemDetail(e) ?? 'Não deu pra salvar agora. Tente de novo em instantes.',
          );
        },
      });
  }

  protected year(t: TitleCard): string {
    return t.year ? ` (${t.year})` : '';
  }

  private fetch(
    q: string,
    kind: MediaType,
    page: number,
  ): Observable<Page<TitleCard> & { more: boolean }> {
    if (!q) {
      return this.catalog
        .titles({ type: kind, sort: 'POPULARITY', page, size: PAGE_SIZE })
        .pipe(map((p) => ({ ...p, more: p.page + 1 < p.totalPages })));
    }
    // a busca não filtra por tipo: o filtro de Filmes/Séries é feito aqui
    return this.catalog.search(q, page, PAGE_SIZE).pipe(
      map((p) => ({
        ...p,
        items: (p.items as TitleCard[]).filter((t) => t.mediaType === kind),
        more: p.page + 1 < p.totalPages,
      })),
    );
  }

  private show(page: (Page<TitleCard> & { more: boolean }) | null, append: boolean): void {
    this.loading.set(false);
    if (!page) {
      if (!append) this.items.set([]);
      this.failed.set(true);
      return;
    }
    this.page = page.page;
    this.hasMore.set(page.more);
    this.items.update((list) => {
      if (!append) return page.items;
      const seen = new Set(list.map((t) => t.id));
      return [...list, ...page.items.filter((t) => !seen.has(t.id))];
    });
  }
}
