import { ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { Genre, MediaType } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Segmented, SegmentOption } from '../../shared/ui/segmented';
import { roomFilters } from './match-format';

type DeckType = 'ALL' | MediaType;

/**
 * Modal "Criar uma sala": só o tipo e o gênero do deck. O nome da sala é o nome de quem cria e o
 * mascote é sorteado pelo back. Criou, vai direto para a sala de espera.
 */
@Component({
  selector: 'ww-match-create-dialog',
  imports: [Button, Icon, Segmented],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
  <dialog #dialog class="modal" aria-labelledby="criar-titulo" (close)="onDialogClose()" (click)="onBackdrop($event)">
    <form method="dialog" class="sheet" (submit)="create($event)">
      <header class="sheet-head">
        <div>
          <h2 id="criar-titulo">Criar uma sala</h2>
          <p class="sheet-lead">A galera entra, espera você começar e todo mundo vota no mesmo deck.</p>
        </div>
        <button type="button" class="sheet-x" (click)="closeModal()" aria-label="Fechar">
          <ww-icon name="x" [size]="18" [stroke]="2.2" />
        </button>
      </header>

      <div class="preview" aria-hidden="true">
        <span class="preview-mascot">?</span>
        <span class="preview-text">
          <strong>{{ myName() }}</strong>
          <span>{{ previewFilters() }}</span>
        </span>
      </div>

      <div class="field">
        <span class="label">O que entra no deck</span>
        <ww-segmented label="O que entra no deck" [options]="types" [(value)]="type" />
      </div>
      <label class="field">
        <span class="label">Gênero</span>
        <select [value]="genre()" (change)="genre.set($any($event.target).value)">
          <option value="">Todos os gêneros</option>
          @for (g of genres(); track g.id) {
            <option [value]="g.slug">{{ g.name }}</option>
          }
        </select>
      </label>

      @if (createError(); as error) {
        <p class="error" role="alert">{{ error }}</p>
      }

      <footer class="sheet-foot">
        <button type="button" class="text-btn" (click)="closeModal()">Cancelar</button>
        <button wwButton variant="primary" type="submit" [disabled]="creating()">
          {{ creating() ? 'Criando…' : 'Criar sala' }}
        </button>
      </footer>
    </form>
  </dialog>
  `,
  styles: `
    .modal {
      width: min(480px, calc(100vw - 32px));
      max-height: calc(100dvh - 32px);
      padding: 0;
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-lg);
      background: var(--surface);
      color: var(--text);
      box-shadow: var(--ww-shadow-lift);
    }
    .modal[open] {
      animation: modal-in var(--ww-medium) var(--ww-ease-spring) both;
    }
    .modal::backdrop {
      background: rgba(12, 12, 16, 0.35);
      backdrop-filter: blur(6px);
    }
    .sheet {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-5);
      padding: var(--ww-space-6);
    }
    .sheet-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--ww-space-3);
    }
    .sheet-head h2 {
      font-size: 22px;
      font-weight: 700;
    }
    .sheet-lead {
      margin-top: 4px;
      color: var(--text-2);
      font-size: 14px;
    }
    .sheet-x {
      display: grid;
      place-items: center;
      flex-shrink: 0;
      width: 36px;
      height: 36px;
      margin: -4px -6px 0 0;
      border: 0;
      border-radius: 50%;
      background: var(--surface-2);
      color: var(--text-2);
      cursor: pointer;
    }
    .sheet-x:focus-visible {
      outline: 2px solid var(--focus-ring);
    }
    .preview {
      display: flex;
      align-items: center;
      gap: var(--ww-space-3);
      padding: var(--ww-space-3);
      border-radius: var(--ww-radius-md);
      background: var(--surface-2);
    }
    .preview-mascot {
      display: grid;
      place-items: center;
      flex-shrink: 0;
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: var(--ww-mascot-indigo);
      color: var(--ww-mascot-pink);
      font-family: var(--ww-font-display);
      font-size: 26px;
      font-weight: 800;
    }
    .preview-text {
      flex-grow: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      font-size: 13px;
    }
    .preview-text strong {
      font-size: 15px;
    }
    .preview-text span {
      color: var(--text-2);
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: 6px;
      width: 100%;
    }
    .label {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-2);
    }
    select {
      min-height: 48px;
      padding: 0 var(--ww-space-3);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-sm);
      background: var(--bg);
      color: var(--text);
      font: inherit;
      font-size: 16px;
    }
    select:focus-visible {
      outline: 2px solid var(--focus-ring);
      outline-offset: 1px;
    }
    .error {
      color: var(--ww-pink-strong);
      font-size: 14px;
      font-weight: 600;
    }
    .sheet-foot {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: var(--ww-space-2);
      margin-top: var(--ww-space-2);
    }
    .text-btn {
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-4);
      border: 0;
      border-radius: var(--ww-radius-pill);
      background: none;
      color: var(--text-2);
      font: inherit;
      font-weight: 600;
      cursor: pointer;
    }
    .text-btn:hover {
      color: var(--text);
    }
    .text-btn:focus-visible {
      outline: 2px solid var(--focus-ring);
    }
    @keyframes modal-in {
      from {
        transform: translateY(12px) scale(0.96);
        opacity: 0;
      }
    }
  `,
})
export class MatchCreateDialog {
  private readonly catalog = inject(CatalogApi);
  private readonly social = inject(SocialApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly types: SegmentOption<DeckType>[] = [
    { value: 'ALL', label: 'Tudo' },
    { value: 'MOVIE', label: 'Filmes' },
    { value: 'SERIES', label: 'Séries' },
  ];
  protected readonly type = signal<DeckType>('ALL');
  protected readonly genre = signal('');
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  /** gêneros que têm título no tipo escolhido; troca a lista quando muda o tipo */
  protected readonly genres = signal<Genre[]>([]);

  protected readonly myName = computed(() => this.auth.user()?.displayName ?? 'Você');
  protected readonly previewFilters = computed(() => {
    const type = this.type();
    const genre = this.genres().find((g) => g.slug === this.genre()) ?? null;
    return roomFilters({ mediaType: type === 'ALL' ? null : type, genre });
  });

  constructor() {
    toObservable(this.type)
      .pipe(
        switchMap((type) => this.catalog.genres(type === 'ALL' ? undefined : type).pipe(catchError(() => of([])))),
        takeUntilDestroyed(),
      )
      .subscribe((genres) => {
        this.genres.set(genres.filter((g) => g.titleCount > 0));
        if (this.genre() && !genres.some((g) => g.slug === this.genre())) this.genre.set('');
      });
  }

  open(): void {
    this.createError.set(null);
    const el = this.dialog()?.nativeElement;
    if (el && typeof el.showModal === 'function' && !el.open) el.showModal();
  }

  protected closeModal(): void {
    this.dialog()?.nativeElement.close();
  }

  protected onDialogClose(): void {
    this.createError.set(null);
  }

  /** Clicar fora do conteúdo (no fundo escurecido) fecha o modal. */
  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog()?.nativeElement) this.closeModal();
  }

  protected create(event: Event): void {
    event.preventDefault();
    if (this.creating()) return;
    this.creating.set(true);
    this.createError.set(null);
    const type = this.type();
    this.social
      .createRoom({ mediaType: type === 'ALL' ? null : type, genre: this.genre() || null })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (room) => {
          this.closeModal();
          this.router.navigate(['/match', room.code]);
        },
        error: () => {
          this.creating.set(false);
          this.createError.set('Não deu pra criar a sala. Tente de novo.');
        },
      });
  }
}
