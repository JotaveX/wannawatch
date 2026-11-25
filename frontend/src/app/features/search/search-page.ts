import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { SearchHit } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { TopNav } from '../../layout/top-nav';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Mascot } from '../../shared/ui/mascot';
import { Poster } from '../../shared/ui/poster';

export const MIN_CHARS = 2;
const PAGE_SIZE = 20;
const DEBOUNCE_MS = 300;

type State =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ok'; term: string; items: SearchHit[]; page: number; totalPages: number; totalItems: number };

/** Por que o título apareceu: "com Pedro Pascal", "Direção de Christopher Nolan", "Gênero: Terror". */
export function matchLabel(hit: SearchHit): string | null {
  switch (hit.matchedBy.kind) {
    case 'PERSON':
      return hit.matchedBy.label;
    case 'GENRE':
      return hit.matchedBy.label ? `Gênero: ${hit.matchedBy.label}` : null;
    default:
      return null;
  }
}

@Component({
  selector: 'ww-search-page',
  imports: [RouterLink, TopNav, Button, Icon, Mascot, Poster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './search-page.html',
  styleUrl: './search-page.css',
})
export class SearchPage {
  /** ?q= da URL */
  readonly q = input<string | undefined>();

  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  protected readonly term = signal('');
  protected readonly state = signal<State>({ status: 'idle' });
  protected readonly loadingMore = signal(false);
  /** o resultado, quando há um */
  protected readonly result = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s : null;
  });
  protected readonly genres = toSignal(this.api.genres().pipe(catchError(() => of([]))), { initialValue: [] });

  protected readonly minChars = MIN_CHARS;
  protected readonly metaLine = metaLine;
  protected readonly matchLabel = matchLabel;
  protected readonly titleLink = titleLink;

  /** último termo que foi parar na URL (para não brigar com quem está digitando) */
  private urlTerm: string | null = null;

  constructor() {
    // URL → campo: só quando a URL muda por fora (ex.: voltar do navegador)
    effect(() => {
      const fromUrl = (this.q() ?? '').trim();
      untracked(() => {
        if (fromUrl !== this.urlTerm) {
          this.urlTerm = fromUrl;
          this.term.set(fromUrl);
        }
      });
    });

    toObservable(this.term)
      .pipe(
        map((t) => t.trim()),
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        tap((t) => this.syncUrl(t)),
        switchMap((t) =>
          t.length < MIN_CHARS
            ? of<State>({ status: 'idle' })
            : this.api.search(t, 0, PAGE_SIZE).pipe(
                map((page): State => ({ status: 'ok', term: t, ...page })),
                catchError(() => of<State>({ status: 'error' })),
                startWith<State>({ status: 'loading' }),
              ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((s) => this.state.set(s));

    afterNextRender(() => {
      if (!this.term()) this.field().nativeElement.focus();
    });
  }

  protected onInput(value: string): void {
    this.term.set(value);
  }

  protected clear(): void {
    this.term.set('');
    this.field().nativeElement.focus();
  }

  protected pick(name: string): void {
    this.term.set(name);
  }

  protected retry(): void {
    // força uma nova busca com o mesmo termo
    const t = this.term();
    this.term.set(t + ' ');
    queueMicrotask(() => this.term.set(t));
  }

  protected loadMore(): void {
    const s = this.state();
    if (s.status !== 'ok' || this.loadingMore()) return;
    this.loadingMore.set(true);
    this.api.search(s.term, s.page + 1, PAGE_SIZE).subscribe({
      next: (page) => {
        const now = this.state();
        if (now.status === 'ok' && now.term === s.term) {
          this.state.set({ ...now, items: [...now.items, ...page.items], page: page.page, totalPages: page.totalPages });
        }
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  private syncUrl(t: string): void {
    if (t === this.urlTerm) return;
    this.urlTerm = t;
    this.router.navigate([], { queryParams: { q: t || null }, replaceUrl: true });
  }
}
