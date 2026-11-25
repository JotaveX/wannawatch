import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  model,
  signal,
  viewChild,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, Observable, of, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { MediaType, TitleCard } from '../../core/api/models';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Poster } from '../../shared/ui/poster';
import { Segmented, SegmentOption } from '../../shared/ui/segmented';

type Kind = 'ALL' | MediaType;

const PAGE_SIZE = 30;

let nextId = 0;

/**
 * Escolhe um título do catálogo num modal: abre com os mais populares e filtra pelo nome
 * conforme a pessoa digita. Fora do modal mostra o escolhido com "Trocar" e "Remover".
 */
@Component({
  selector: 'ww-favorite-title-picker',
  imports: [Button, Icon, Poster, Segmented],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './favorite-title-picker.html',
  styleUrl: './favorite-title-picker.css',
})
export class FavoriteTitlePicker {
  readonly value = model<TitleCard | null>(null);
  /** id do rótulo visível do campo */
  readonly labelledBy = input<string | null>(null);

  private readonly catalog = inject(CatalogApi);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly id = nextId++;
  protected readonly titleId = `fav-titulo-${this.id}`;
  protected readonly statusId = `fav-status-${this.id}`;

  protected readonly kinds: SegmentOption<Kind>[] = [
    { value: 'ALL', label: 'Tudo' },
    { value: 'MOVIE', label: 'Filmes' },
    { value: 'SERIES', label: 'Séries' },
  ];
  protected readonly kindFilter = signal<Kind>('ALL');
  protected readonly query = signal('');
  protected readonly loading = signal(false);

  /** Busca de verdade só a partir de 2 letras; antes disso, os populares. */
  private readonly term = computed(() => {
    const q = this.query().trim();
    return q.length >= 2 ? q : '';
  });
  protected readonly searching = computed(() => !!this.term());

  protected readonly results = toSignal(
    toObservable(computed(() => ({ q: this.term(), kind: this.kindFilter() }))).pipe(
      debounceTime(250),
      distinctUntilChanged((a, b) => a.q === b.q && a.kind === b.kind),
      switchMap(({ q, kind }) => {
        this.loading.set(true);
        return this.fetch(q, kind).pipe(
          catchError(() => of(null)),
          finalize(() => this.loading.set(false)),
        );
      }),
    ),
    { initialValue: [] as TitleCard[] },
  );

  /** null = a busca falhou */
  protected readonly failed = computed(() => this.results() === null);
  protected readonly items = computed(() => this.results() ?? []);

  private fetch(q: string, kind: Kind): Observable<TitleCard[]> {
    const type = kind === 'ALL' ? undefined : kind;
    if (!q) {
      return this.catalog.titles({ type, sort: 'POPULARITY', size: PAGE_SIZE }).pipe(map((page) => page.items));
    }
    // a busca não filtra por tipo: o filtro de Filmes/Séries é feito aqui
    return this.catalog
      .search(q, 0, PAGE_SIZE)
      .pipe(map((page) => (page.items as TitleCard[]).filter((t) => !type || t.mediaType === type)));
  }

  protected open(): void {
    this.query.set('');
    const el = this.dialog()?.nativeElement;
    if (el && typeof el.showModal === 'function' && !el.open) el.showModal();
  }

  protected close(): void {
    this.dialog()?.nativeElement.close();
  }

  /** Clicar fora do conteúdo (no fundo escurecido) fecha o modal. */
  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog()?.nativeElement) this.close();
  }

  protected pick(title: TitleCard): void {
    this.value.set(title);
    this.close();
  }

  protected kind(t: TitleCard): string {
    return t.mediaType === 'SERIES' ? 'Série' : 'Filme';
  }
}
