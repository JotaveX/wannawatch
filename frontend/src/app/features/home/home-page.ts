import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CatalogApi } from '../../core/api/catalog-api';
import { HomeResponse } from '../../core/api/models';
import { TopNav } from '../../layout/top-nav';
import { SocialMock } from '../../mocks/social.mock';
import { Button } from '../../shared/ui/button';
import { buildHomeRows } from './home-rows';
import { HomeHero } from './home-hero';
import { TitleRow } from './title-row';
import { TopCard } from './top-card';
import { WideCard } from './wide-card';

type State = { status: 'loading' } | { status: 'error' } | { status: 'ok'; data: HomeResponse };

@Component({
  selector: 'ww-home-page',
  imports: [TopNav, HomeHero, TitleRow, TopCard, WideCard, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (state().status) {
      @case ('ok') {
        @if (hero(); as hero) {
          <ww-home-hero [hero]="hero"><ww-top-nav /></ww-home-hero>
        } @else {
          <ww-top-nav />
        }
        <div class="rows" [class.under-hero]="!!hero()">
          @for (row of rows(); track row.key) {
            <ww-title-row [heading]="row.title">
              @if (row.kind === 'TOP10') {
                @for (card of row.items; track card.id; let i = $index) {
                  <ww-top-card [card]="card" [rank]="i + 1" />
                }
              } @else {
                @for (item of row.items; track item.card.id) {
                  <ww-wide-card [item]="item" />
                }
              }
            </ww-title-row>
          } @empty {
            <p class="message">O catálogo ainda está vazio. A importação da TMDB roda na primeira subida do back-end.</p>
          }
        </div>
      }
      @case ('error') {
        <ww-top-nav />
        <div class="message" role="alert">
          <h1>Não deu pra carregar a Home</h1>
          <p>Confira se o back-end está rodando e tente de novo.</p>
          <button wwButton variant="light" (click)="load()">Tentar de novo</button>
        </div>
      }
      @default {
        <ww-top-nav />
        <div class="skeleton" aria-busy="true" aria-label="Carregando">
          <div class="sk-hero"></div>
          @for (n of [1, 2, 3]; track n) {
            <div class="sk-row">
              <span class="sk-title"></span>
              <div>
                @for (c of [1, 2, 3, 4, 5]; track c) {
                  <span class="sk-card"></span>
                }
              </div>
            </div>
          }
        </div>
      }
    }
  `,
  styles: `
    :host {
      display: block;
      min-height: 100dvh;
      padding-bottom: 72px;
      background: var(--ww-bg-dark);
      color: var(--ww-text-on-dark);
      overflow-x: hidden;
    }
    .rows {
      position: relative;
      z-index: 2;
      display: flex;
      flex-direction: column;
      gap: 36px;
      padding-top: var(--ww-space-6);
    }
    .rows.under-hero {
      margin-top: -116px;
      padding-top: 0;
    }
    .message {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: var(--ww-space-3);
      padding: var(--ww-space-10) var(--ww-page-x);
      color: var(--ww-text-on-dark-2);
    }
    .message h1 {
      font-size: 28px;
      color: var(--ww-white);
    }
    .skeleton {
      display: flex;
      flex-direction: column;
      gap: 36px;
      padding: 0 var(--ww-page-x);
    }
    .sk-hero {
      height: 360px;
      border-radius: var(--ww-radius-md);
      background: var(--ww-surface-dark);
    }
    .sk-row {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-3);
    }
    .sk-row div {
      display: flex;
      gap: var(--ww-space-2);
      overflow: hidden;
    }
    .sk-title {
      width: 220px;
      height: 20px;
      border-radius: 6px;
      background: var(--ww-surface-dark);
    }
    .sk-card {
      flex-shrink: 0;
      width: 260px;
      aspect-ratio: 16 / 9;
      border-radius: var(--ww-radius-sm);
      background: var(--ww-surface-dark);
    }
    @media (prefers-reduced-motion: no-preference) {
      .skeleton span,
      .sk-hero {
        animation: pulse 1.4s ease-in-out infinite;
      }
    }
    @keyframes pulse {
      50% {
        opacity: 0.55;
      }
    }
  `,
})
export class HomePage {
  private readonly api = inject(CatalogApi);
  private readonly social = inject(SocialMock);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<State>({ status: 'loading' });
  private readonly data = computed(() => {
    const s = this.state();
    return s.status === 'ok' ? s.data : null;
  });
  protected readonly hero = computed(() => this.data()?.hero ?? null);
  // depende do modo grupo/solo (lido dentro de buildHomeRows), então troca na hora
  protected readonly rows = computed(() => {
    const data = this.data();
    return data ? buildHomeRows(data, this.social) : [];
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set({ status: 'loading' });
    this.api
      .home()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.state.set({ status: 'ok', data }),
        error: () => this.state.set({ status: 'error' }),
      });
  }
}
