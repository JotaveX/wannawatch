import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TitleCard } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { LikesStore, likedByLabel } from '../../core/social/likes-store';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { Poster } from '../../shared/ui/poster';
import { Decision } from './swipe-store';

/** Distância (px) para a carta ir embora ao soltar. */
export const THRESHOLD_X = 100;
export const THRESHOLD_Y = 110;
const FLY_MS = 260;

/**
 * Para onde o arrasto aponta, ou null se ainda não passou do limite.
 * Sem `vertical` (Match), só vale para os lados.
 */
export function dragDecision(dx: number, dy: number, vertical = true): Decision | null {
  if (!vertical || Math.abs(dx) >= Math.abs(dy)) {
    if (dx >= THRESHOLD_X) return 'like';
    if (dx <= -THRESHOLD_X) return 'pass';
  } else {
    if (dy <= -THRESHOLD_Y) return 'maybe';
    if (dy >= THRESHOLD_Y) return 'seen';
  }
  return null;
}

const OUT: Record<Decision, [number, number, number]> = {
  like: [1.4, 0, 18],
  pass: [-1.4, 0, -18],
  maybe: [0, -1.3, 0],
  seen: [0, 1.3, 0],
};

/**
 * Carta do Swipe. Arrasta com mouse, dedo ou caneta (Pointer Events):
 * → quero ver · ← passa · ↑ talvez · ↓ já vi. Os botões da página chamam fling().
 */
@Component({
  selector: 'ww-swipe-card',
  imports: [RouterLink, Icon, IconButton, Poster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.top]': 'interactive()',
    '[style.transform]': 'transform()',
    '[style.transition]': 'dragging() ? "none" : null',
    '(pointerdown)': 'onDown($event)',
    '(pointermove)': 'onMove($event)',
    '(pointerup)': 'onUp()',
    '(pointercancel)': 'reset()',
    // sem isso o navegador começa a arrastar a imagem do pôster e cancela o nosso arrasto
    '(dragstart)': '$event.preventDefault()',
  },
  template: `
    <article [attr.aria-labelledby]="'card-' + card().id" [attr.aria-hidden]="interactive() ? null : true">
      <div class="media">
        <ww-poster ratio="fill" [image]="card().poster" [widths]="[342, 780]" sizes="420px"
                   [seed]="card().id" [title]="card().title" [eager]="true" />
        @if (likedBy(); as likedBy) {
          <span class="badge">{{ likedBy }}</span>
        }
        @if (interactive()) {
          <a wwIconButton variant="light" class="info" [routerLink]="link()"
             [label]="'Mais informações sobre ' + card().title">
            <ww-icon name="info" />
          </a>
        }
        <span class="stamp like" [style.opacity]="stamp().like" aria-hidden="true">Quero ver</span>
        <span class="stamp pass" [style.opacity]="stamp().pass" aria-hidden="true">Passo</span>
        <span class="stamp maybe" [style.opacity]="stamp().maybe" aria-hidden="true">Talvez</span>
        <span class="stamp seen" [style.opacity]="stamp().seen" aria-hidden="true">Já vi</span>
      </div>
      <div class="text">
        <span class="meta">{{ meta() }}</span>
        <h2 [id]="'card-' + card().id">{{ card().title }}</h2>
        @if (card().overview) {
          <p>{{ card().overview }}</p>
        }
      </div>
    </article>
  `,
  styleUrl: './swipe-card.css',
})
export class SwipeCard {
  readonly card = input.required<TitleCard>();
  /** só a carta de cima arrasta e tem link */
  readonly interactive = input(false);
  /** false = só esquerda/direita (no Match não tem "talvez" nem "já vi") */
  readonly vertical = input(true);
  readonly decided = output<Decision>();

  private readonly likes = inject(LikesStore);
  /** "Bia curtiu" */
  protected readonly likedBy = computed(() => likedByLabel(this.likes.friends(this.card().id)));
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly dx = signal(0);
  protected readonly dy = signal(0);
  protected readonly dragging = signal(false);
  readonly leaving = signal<Decision | null>(null);
  private start: { x: number; y: number; id: number } | null = null;

  protected readonly meta = computed(() => metaLine(this.card()));
  protected readonly link = computed(() => titleLink(this.card()));

  protected readonly transform = computed(() => {
    if (!this.interactive()) return null;
    const leaving = this.leaving();
    if (leaving) {
      const [x, y, r] = OUT[leaving];
      return `translate(${x * 100}vw, ${y * 100}vh) rotate(${r}deg)`;
    }
    const dx = this.dx();
    const dy = this.vertical() ? this.dy() : 0;
    return dx || dy ? `translate(${dx}px, ${dy}px) rotate(${dx / 18}deg)` : null;
  });

  /** opacidade de cada carimbo (0–1) */
  protected readonly stamp = computed(() => {
    const leaving = this.leaving();
    if (leaving) return { like: 0, pass: 0, maybe: 0, seen: 0, [leaving]: 1 };
    const dx = this.dx();
    const dy = this.dy();
    const horizontal = !this.vertical() || Math.abs(dx) >= Math.abs(dy);
    const clamp = (v: number) => Math.max(0, Math.min(1, v));
    return {
      like: horizontal ? clamp(dx / THRESHOLD_X) : 0,
      pass: horizontal ? clamp(-dx / THRESHOLD_X) : 0,
      maybe: horizontal ? 0 : clamp(-dy / THRESHOLD_Y),
      seen: horizontal ? 0 : clamp(dy / THRESHOLD_Y),
    };
  });

  /** Joga a carta para fora e avisa a página quando terminar. */
  fling(decision: Decision): void {
    if (this.leaving()) return;
    this.dragging.set(false);
    this.leaving.set(decision);
    const reduced = matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    setTimeout(() => this.decided.emit(decision), reduced ? 0 : FLY_MS);
  }

  protected onDown(event: PointerEvent): void {
    if (!this.interactive() || this.leaving() || event.button !== 0) return;
    // o botão de informações continua clicável
    if ((event.target as Element).closest('a, button')) return;
    this.start = { x: event.clientX, y: event.clientY, id: event.pointerId };
    this.host.nativeElement.setPointerCapture?.(event.pointerId);
    this.dragging.set(true);
  }

  protected onMove(event: PointerEvent): void {
    if (!this.start || event.pointerId !== this.start.id) return;
    this.dx.set(event.clientX - this.start.x);
    this.dy.set(event.clientY - this.start.y);
  }

  protected onUp(): void {
    if (!this.start) return;
    const decision = dragDecision(this.dx(), this.dy(), this.vertical());
    this.start = null;
    if (decision) this.fling(decision);
    else this.reset();
  }

  protected reset(): void {
    this.start = null;
    this.dragging.set(false);
    this.dx.set(0);
    this.dy.set(0);
  }
}
