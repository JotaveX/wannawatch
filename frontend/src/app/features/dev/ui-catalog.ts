import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { SocialMock, ViewMode } from '../../mocks/social.mock';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { AppMark, Logo } from '../../shared/ui/logo';
import { MatchBadge } from '../../shared/ui/match-badge';
import { Poster } from '../../shared/ui/poster';
import { Segmented, SegmentOption } from '../../shared/ui/segmented';

/** Vitrine dos componentes base (só existe em desenvolvimento: /dev/componentes). */
@Component({
  selector: 'ww-ui-catalog',
  imports: [Button, Icon, IconButton, Logo, AppMark, MatchBadge, Poster, Segmented],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (theme of themes; track theme) {
      <section [attr.data-theme]="theme" [attr.aria-label]="'Tema ' + theme">
        <h2>Tema {{ theme === 'light' ? 'claro' : 'escuro' }}</h2>

        <div class="row">
          <ww-logo [size]="32" />
          <ww-app-mark [size]="40" />
          <ww-match-badge [percent]="social.matchPercent(42)" />
        </div>

        <div class="row">
          <button wwButton variant="primary">Quero ver</button>
          <a wwButton variant="light" href="#" (click)="$event.preventDefault()"><ww-icon name="play" [size]="16" />Assistir trailer</a>
          <button wwButton variant="glass"><ww-icon name="info" [size]="18" />Mais informações</button>
          <button wwButton variant="outline">Votar no grupo</button>
          <button wwButton variant="soft">Placeholder</button>
        </div>

        <div class="row">
          <button wwIconButton label="Voltar o último"><ww-icon name="undo" [size]="18" /></button>
          <button wwIconButton label="Passar" size="lg"><ww-icon name="x" [size]="24" /></button>
          <button wwIconButton label="Talvez" size="md" class="maybe">?</button>
          <button wwIconButton label="Quero ver" size="lg" variant="pink"><ww-icon name="heart-filled" [size]="26" /></button>
          <button wwIconButton label="Já assisti"><ww-icon name="eye" [size]="18" /></button>
          <button wwIconButton label="Salvar na lista" variant="light" size="md"><ww-icon name="bookmark" /></button>
          <button wwIconButton label="Buscar" variant="glass"><ww-icon name="search" [size]="18" /></button>
        </div>

        <div class="row">
          <ww-segmented label="Modo" [options]="modes" [(value)]="mode" />
          <ww-segmented label="Tipo" variant="pill" [options]="tabs" [(value)]="tab" />
        </div>

        <div class="posters">
          @for (seed of seeds; track seed) {
            <ww-poster class="poster" [seed]="seed" [title]="'Título de exemplo ' + seed" />
          }
          <ww-poster class="poster" [seed]="1" title="Imagem quebrada"
                     [image]="{ small: 'https://image.tmdb.org/t/p/w342/nao-existe.jpg', large: 'x' }" />
        </div>
        <ww-poster class="backdrop" ratio="backdrop" [seed]="9" title="Backdrop sem imagem" />
      </section>
    }
  `,
  styles: `
    section {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-6);
      padding: var(--ww-space-10) var(--ww-page-x);
      background: var(--bg);
      color: var(--text);
    }
    h2 {
      font-size: 24px;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--ww-space-3);
    }
    .maybe {
      font-family: var(--ww-font-display);
      font-weight: 700;
      font-size: 24px;
      color: var(--ww-yellow-strong);
    }
    .posters {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
      gap: var(--ww-space-3);
    }
    .poster {
      border-radius: var(--ww-radius-sm);
    }
    .backdrop {
      max-width: 420px;
      border-radius: var(--ww-radius-sm);
    }
  `,
})
export class UiCatalog {
  protected readonly social = inject(SocialMock);
  protected readonly themes = ['light', 'dark'] as const;
  protected readonly seeds = [0, 1, 2, 3, 4, 5];
  protected readonly modes: SegmentOption<ViewMode>[] = [
    { value: 'group', label: 'Grupo' },
    { value: 'solo', label: 'Solo' },
  ];
  protected readonly tabs: SegmentOption<'movie' | 'series'>[] = [
    { value: 'movie', label: 'Filmes' },
    { value: 'series', label: 'Séries' },
  ];
  protected readonly mode = this.social.mode;
  protected readonly tab = signal<'movie' | 'series'>('movie');
}
