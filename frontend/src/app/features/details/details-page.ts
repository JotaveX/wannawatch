import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { MediaType, TitleDetail } from '../../core/api/models';
import { length, metaLine, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { TopNav } from '../../layout/top-nav';
import { LikesStore, likedByLabel } from '../../core/social/likes-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { AppMark } from '../../shared/ui/logo';
import { Poster } from '../../shared/ui/poster';
import { TitleRow } from '../../shared/ui/title-row';
import { RowItem, WideCard } from '../../shared/ui/wide-card';
import { crewGroups, infoItems, initials, providerGroups } from './details-view';

type State =
  | { status: 'loading' }
  | { status: 'notfound' }
  | { status: 'error' }
  | { status: 'ok'; detail: TitleDetail };

/** Página de detalhes de filme ou série (/filme/:id e /serie/:id). Sempre no tema escuro. */
@Component({
  selector: 'ww-details-page',
  imports: [RouterLink, TopNav, Button, Icon, AppMark, Poster, TitleRow, WideCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './details-page.html',
  styleUrl: './details-page.css',
})
export class DetailsPage {
  /** parâmetro :id da rota */
  readonly id = input.required<string>();
  /** tipo esperado pela rota (data.mediaType) */
  readonly mediaType = input<MediaType>('MOVIE');

  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly pageTitle = inject(Title);
  private readonly likes = inject(LikesStore);
  protected readonly retry = signal(0);

  protected readonly state = signal<State>({ status: 'loading' });

  protected readonly detail = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.detail : null;
  });
  protected readonly type = computed(() => (this.detail() ? typeLabel(this.detail()!) : ''));
  protected readonly meta = computed(() => {
    const d = this.detail();
    if (!d) return [];
    return [d.genres.map((g) => g.name).slice(0, 3).join(', '), d.year ? String(d.year) : null, length(d)].filter(
      (p): p is string => !!p,
    );
  });
  protected readonly info = computed(() => (this.detail() ? infoItems(this.detail()!) : []));
  protected readonly crew = computed(() => (this.detail() ? crewGroups(this.detail()!.crew) : []));
  protected readonly providers = computed(() => (this.detail() ? providerGroups(this.detail()!.watchProviders) : []));
  protected readonly similar = computed<RowItem[]>(() =>
    (this.detail()?.similar ?? []).map((card) => ({ card, sub: metaLine(card) })),
  );
  protected readonly initials = initials;
  /** "Bia e Caio curtiram" */
  protected readonly likedBy = computed(() => {
    const d = this.detail();
    return d ? likedByLabel(this.likes.friends(d.id)) : null;
  });

  constructor() {
    const request = computed(() => ({ id: Number(this.id()), retry: this.retry() }));
    toObservable(request)
      .pipe(
        switchMap(({ id }) =>
          !Number.isInteger(id) || id <= 0
            ? of<State>({ status: 'notfound' })
            : this.api.title(id).pipe(
                map((detail): State => ({ status: 'ok', detail })),
                catchError((e: HttpErrorResponse) => of<State>({ status: e.status === 404 ? 'notfound' : 'error' })),
                startWith<State>({ status: 'loading' }),
              ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => {
        if (state.status === 'ok' && state.detail.mediaType !== this.mediaType()) {
          // /filme/7 que na verdade é série → vai para /serie/7
          this.router.navigate(titleLink(state.detail), { replaceUrl: true });
          return;
        }
        this.state.set(state);
        if (state.status === 'ok') this.likes.ensure([state.detail.id]);
        if (state.status === 'ok') this.pageTitle.setTitle(`${state.detail.title} · wannawatch?`);
        if (state.status === 'notfound') this.pageTitle.setTitle('Não encontrado · wannawatch?');
      });
  }
}
