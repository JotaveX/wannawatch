import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { DiscoverRequest, DiscoverSource, MediaType } from '../../core/api/models';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { SegmentOption, Segmented } from '../../shared/ui/segmented';

type TypeFilter = 'ALL' | MediaType;

const MAX_LIMIT = 20;

/**
 * "Buscar mais vídeos": quantos títulos, a opção de pular quem já tem vídeo aprovado e, na busca
 * avançada, de onde saem os títulos (populares, em alta…), tipo, gênero, ano e rebusca.
 */
@Component({
  selector: 'ww-discover-form',
  imports: [Button, Icon, Segmented],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (submit)="$event.preventDefault(); submit()">
      <div class="top">
        <div class="text">
          <h2>Buscar mais vídeos</h2>
          <p>Cada título gasta 1 das 100 buscas do dia. Só os verticais ficam.</p>
        </div>
        <div class="controls">
          <div class="stepper">
            <button
              type="button"
              aria-label="Menos títulos"
              [disabled]="limit() <= 1"
              (click)="setLimit(limit() - 1)"
            >
              <ww-icon name="minus" [size]="18" />
            </button>
            <label>
              <span class="visually-hidden">Quantos títulos buscar</span>
              <input
                type="number"
                min="1"
                [max]="max"
                [value]="limit()"
                (change)="setLimit(+$any($event.target).value)"
              />
            </label>
            <button
              type="button"
              aria-label="Mais títulos"
              [disabled]="limit() >= max"
              (click)="setLimit(limit() + 1)"
            >
              <ww-icon name="plus" [size]="18" />
            </button>
          </div>
          <button wwButton variant="primary" type="submit" [disabled]="busy()">
            @if (busy()) {
              <span class="spinner" aria-hidden="true"></span> Buscando…
            } @else {
              <ww-icon name="search" [size]="18" /> Buscar no YouTube
            }
          </button>
        </div>
      </div>

      <div class="row">
        <label class="check">
          <input
            type="checkbox"
            [checked]="skipApproved()"
            (change)="skipApproved.set($any($event.target).checked)"
          />
          Pular títulos que já têm vídeo aprovado
        </label>
        <button
          type="button"
          class="more"
          aria-controls="discover-advanced"
          [attr.aria-expanded]="advanced()"
          (click)="advanced.set(!advanced())"
        >
          Busca avançada
          @if (activeFilters(); as n) {
            <span class="badge">{{ n }}</span>
          }
          <ww-icon [name]="advanced() ? 'chevron-up' : 'chevron-down'" [size]="16" />
        </button>
      </div>

      @if (advanced()) {
        <fieldset id="discover-advanced" animate.enter="open">
          <legend class="visually-hidden">Busca avançada</legend>
          <div class="field">
            <span class="name">De onde</span>
            <ww-segmented label="De onde saem os títulos" [options]="sources" [(value)]="source" />
          </div>
          <div class="field">
            <span class="name">Tipo</span>
            <ww-segmented label="Tipo de título" [options]="types" [(value)]="type" />
          </div>
          <label class="field">
            <span class="name">Gênero</span>
            <select [value]="genre()" (change)="genre.set($any($event.target).value)">
              <option value="">Todos</option>
              @for (g of genreOptions(); track g.slug) {
                <option [value]="g.slug" [selected]="g.slug === genre()">{{ g.name }}</option>
              }
            </select>
          </label>
          <div class="field">
            <span class="name">Lançamento</span>
            <div class="years">
              <label>
                <span class="visually-hidden">Ano inicial</span>
                <input
                  type="number"
                  inputmode="numeric"
                  placeholder="De"
                  min="1900"
                  [max]="thisYear"
                  [value]="yearFrom() ?? ''"
                  (change)="yearFrom.set(year($any($event.target).value))"
                />
              </label>
              <span aria-hidden="true">–</span>
              <label>
                <span class="visually-hidden">Ano final</span>
                <input
                  type="number"
                  inputmode="numeric"
                  placeholder="Até"
                  min="1900"
                  [max]="thisYear"
                  [value]="yearTo() ?? ''"
                  (change)="yearTo.set(year($any($event.target).value))"
                />
              </label>
            </div>
          </div>
          <label class="check wide">
            <input
              type="checkbox"
              [checked]="includeSearched()"
              (change)="includeSearched.set($any($event.target).checked)"
            />
            Buscar de novo títulos que já foram buscados
          </label>
          @if (yearsInverted()) {
            <p class="wide err">O ano inicial está depois do final.</p>
          }
          @if (activeFilters()) {
            <button type="button" class="more wide" (click)="reset()">Limpar filtros</button>
          }
        </fieldset>
      }
    </form>
  `,
  styles: `
    form {
      display: grid;
      gap: var(--ww-space-4);
      padding: var(--ww-space-5);
      border-radius: var(--ww-radius-lg);
      background: var(--surface-2);
    }
    h2,
    p {
      margin: 0;
    }
    h2 {
      font: 700 18px var(--ww-font-display);
    }
    .text p {
      color: var(--text-2);
      font-size: 14px;
    }
    .top,
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--ww-space-3) var(--ww-space-4);
    }
    .controls {
      display: flex;
      flex-wrap: wrap;
      gap: var(--ww-space-3);
    }
    .stepper {
      display: flex;
      align-items: center;
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-pill);
      background: var(--surface);
    }
    .stepper button {
      display: grid;
      place-items: center;
      width: var(--ww-touch);
      height: var(--ww-touch);
      border: 0;
      border-radius: 50%;
      background: none;
      color: var(--text);
      cursor: pointer;
    }
    .stepper button:disabled {
      opacity: 0.35;
      cursor: default;
    }
    input[type='number'] {
      appearance: textfield;
    }
    input::-webkit-inner-spin-button {
      appearance: none;
    }
    .stepper input {
      width: 3ch;
      border: 0;
      background: none;
      color: var(--text);
      font: 700 17px var(--ww-font-ui);
      text-align: center;
    }
    .check {
      display: inline-flex;
      align-items: center;
      gap: var(--ww-space-2);
      min-height: var(--ww-touch);
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }
    .check input {
      width: 20px;
      height: 20px;
      margin: 0;
      accent-color: var(--ww-graphite);
    }
    .more {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-4);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-pill);
      background: var(--surface);
      color: var(--text);
      font: 600 14px var(--ww-font-ui);
      cursor: pointer;
    }
    .more:hover {
      background: var(--hover);
    }
    .badge {
      min-width: 20px;
      padding: 0 6px;
      border-radius: var(--ww-radius-pill);
      background: var(--ww-graphite);
      color: var(--ww-white);
      font-size: 12px;
    }
    fieldset {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: var(--ww-space-4);
      margin: 0;
      padding: var(--ww-space-4) 0 0;
      border: 0;
      border-top: var(--ww-hairline) solid var(--border);
    }
    .field {
      display: grid;
      align-content: start;
      gap: var(--ww-space-2);
    }
    .field ww-segmented {
      justify-self: start;
      max-width: 100%;
      overflow-x: auto;
    }
    .name {
      color: var(--text-2);
      font-size: 13px;
      font-weight: 600;
    }
    select,
    .years input {
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-3);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-sm);
      background: var(--surface);
      color: var(--text);
      font: 500 15px var(--ww-font-ui);
    }
    .years {
      display: flex;
      align-items: center;
      gap: var(--ww-space-2);
    }
    .years input {
      width: 7ch;
    }
    .wide {
      grid-column: 1 / -1;
      justify-self: start;
    }
    .err {
      color: var(--ww-pink-strong);
      font-size: 14px;
      font-weight: 600;
    }
    button:focus-visible,
    input:focus-visible,
    select:focus-visible {
      outline: 3px solid var(--focus-ring);
      outline-offset: 2px;
    }
    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid currentColor;
      border-right-color: transparent;
      border-radius: 50%;
      animation: spin 700ms linear infinite;
    }
    .open {
      animation: open var(--ww-medium) var(--ww-ease-out);
    }
    @keyframes open {
      from {
        opacity: 0;
        transform: translateY(-6px);
      }
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class DiscoverForm {
  readonly busy = input(false);
  readonly search = output<DiscoverRequest>();

  protected readonly max = MAX_LIMIT;
  protected readonly thisYear = new Date().getFullYear();
  protected readonly sources: SegmentOption<DiscoverSource>[] = [
    { value: 'POPULAR', label: 'Populares' },
    { value: 'TRENDING', label: 'Em alta' },
    { value: 'TOP_RATED', label: 'Mais bem avaliados' },
    { value: 'RECENT', label: 'Lançamentos' },
  ];
  protected readonly types: SegmentOption<TypeFilter>[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'MOVIE', label: 'Filmes' },
    { value: 'SERIES', label: 'Séries' },
  ];

  readonly limit = signal(5);
  readonly skipApproved = signal(true);
  readonly advanced = signal(false);
  readonly source = signal<DiscoverSource>('POPULAR');
  readonly type = signal<TypeFilter>('ALL');
  readonly genre = signal('');
  readonly yearFrom = signal<number | null>(null);
  readonly yearTo = signal<number | null>(null);
  readonly includeSearched = signal(false);

  private readonly genres = toSignal(
    inject(CatalogApi)
      .genres()
      .pipe(catchError(() => of([]))),
    { initialValue: [] },
  );

  /** só os gêneros que existem para o tipo escolhido */
  protected readonly genreOptions = computed(() => {
    const type = this.type();
    return this.genres().filter((g) =>
      type === 'MOVIE' ? g.forMovie : type === 'SERIES' ? g.forSeries : true,
    );
  });

  protected readonly yearsInverted = computed(() => {
    const from = this.yearFrom();
    const to = this.yearTo();
    return from != null && to != null && from > to;
  });

  /** quantos filtros da busca avançada fogem do padrão */
  readonly activeFilters = computed(
    () =>
      [
        this.source() !== 'POPULAR',
        this.type() !== 'ALL',
        !!this.genre(),
        this.yearFrom() != null || this.yearTo() != null,
        this.includeSearched(),
      ].filter(Boolean).length,
  );

  setLimit(n: number): void {
    this.limit.set(Math.min(MAX_LIMIT, Math.max(1, Math.round(n) || 1)));
  }

  protected year(value: string): number | null {
    const n = Math.round(Number(value));
    return value.trim() && Number.isFinite(n) && n > 0 ? n : null;
  }

  reset(): void {
    this.source.set('POPULAR');
    this.type.set('ALL');
    this.genre.set('');
    this.yearFrom.set(null);
    this.yearTo.set(null);
    this.includeSearched.set(false);
  }

  submit(): void {
    if (this.busy() || this.yearsInverted()) return;
    const type = this.type();
    // o gênero escolhido pode não existir para o tipo (ex.: "Reality show" e depois Filmes)
    const genre = this.genreOptions().some((g) => g.slug === this.genre()) ? this.genre() : null;
    this.search.emit({
      limit: this.limit(),
      skipApproved: this.skipApproved(),
      includeSearched: this.includeSearched(),
      source: this.source(),
      type: type === 'ALL' ? null : type,
      genre,
      yearFrom: this.yearFrom(),
      yearTo: this.yearTo(),
    });
  }
}
