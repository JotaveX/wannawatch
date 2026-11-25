import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  OnInit,
  signal,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { length, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { Avatar } from '../../shared/ui/avatar';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { Poster } from '../../shared/ui/poster';
import { SwipeCard } from '../swipe/swipe-card';
import { Decision } from '../swipe/swipe-store';
import { Confetto, confetti, prefersReducedMotion } from './match-celebration';
import { roomFilters } from './match-format';
import { MatchRoomStore } from './match-room-store';
import { MatchWaiting } from './match-waiting';

const KEYS: Record<string, 'like' | 'pass' | 'undo'> = {
  ArrowLeft: 'pass',
  ArrowRight: 'like',
  z: 'undo',
  Z: 'undo',
  Backspace: 'undo',
};

/** Festa do "deu match" antes de abrir os detalhes sozinho. */
export const CELEBRATE_MS = 3600;
/** Carta crescendo até ocupar a tela. */
export const ZOOM_MS = 820;

/** Sala de Match (/match/:code): swipe em conjunto até todo mundo concordar num título. */
@Component({
  selector: 'ww-match-room-page',
  imports: [RouterLink, Avatar, Button, Icon, IconButton, MatchWaiting, Poster, SwipeCard],
  providers: [MatchRoomStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
  templateUrl: './match-room-page.html',
  styleUrls: ['./match-room-page.css', './match-celebration.css'],
})
export class MatchRoomPage implements OnInit {
  /** parâmetro :code da rota */
  readonly code = input.required<string>();

  protected readonly store = inject(MatchRoomStore);
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly cards = viewChildren(SwipeCard);
  private readonly deckEl = viewChild<ElementRef<HTMLElement>>('deck');
  private readonly winnerEl = viewChild<ElementRef<HTMLElement>>('winnerCard');
  private readonly stageEl = viewChild<ElementRef<HTMLElement>>('stage');
  private readonly zoomEl = viewChild<ElementRef<HTMLElement>>('zoom');

  /** confete na tela (vazio = sem festa) */
  protected readonly confetti = signal<Confetto[]>([]);
  /** contando para abrir os detalhes sozinho */
  protected readonly countdown = signal(false);
  /** carta crescendo até a tela cheia */
  protected readonly zooming = signal(false);
  protected readonly celebrateMs = CELEBRATE_MS;
  private zoomFrom: DOMRect | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected readonly toast = signal<string | null>(null);
  protected readonly leaving = signal(false);
  protected readonly titleLink = titleLink;

  protected readonly roomCode = computed(() => this.store.room()?.code ?? this.code().toUpperCase());
  protected readonly filters = computed(() => {
    const room = this.store.room();
    return room ? roomFilters(room) : '';
  });
  /** carta de baixo primeiro, para a de cima ficar por cima no DOM */
  protected readonly stack = computed(() => [this.store.next(), this.store.current()].filter((c) => c !== null));

  protected readonly status = computed(() => {
    if (this.store.alone()) return `Só você por aqui. Mande o código ${this.roomCode()} pra galera.`;
    const last = this.store.last();
    if (last) return `${last.liked ? 'Você quer ver' : 'Passou'} ${last.card.title}`;
    return `${this.store.participants().length} pessoas votando. Vale o primeiro que todo mundo quiser ver.`;
  });

  protected readonly winnerFacts = computed(() => {
    const w = this.store.winner();
    if (!w) return '';
    return [typeLabel(w), w.year ? String(w.year) : null, length(w)].filter(Boolean).join(' · ');
  });

  constructor() {
    effect(() => {
      if (this.store.freshMatch()) untracked(() => this.celebrate());
    });
    // as duas camadas da festa vão para a camada do topo (acima da barra de abas e do menu)
    effect(() => showPopover(this.stageEl()?.nativeElement));
    effect(() => {
      const el = this.zoomEl()?.nativeElement;
      if (el) untracked(() => this.zoom(el));
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  ngOnInit(): void {
    this.store.start(this.code());
  }

  protected act(decision: Extract<Decision, 'like' | 'pass'>): void {
    if (!this.store.current() || this.store.matched()) return;
    const top = this.cards().find((c) => c.interactive());
    if (top?.leaving()) return; // ainda saindo da tela
    if (top) top.fling(decision);
    else this.store.vote(decision === 'like');
  }

  /** A carta só manda like ou pass (o arrasto vertical está desligado). */
  protected onDecided(decision: Decision): void {
    this.store.vote(decision === 'like');
  }

  protected undo(): void {
    if (this.cards().some((c) => c.leaving())) return;
    this.store.undo();
  }

  /** Abre os detalhes do vencedor: a carta cresce até a tela cheia e se dissolve na página. */
  protected openWinner(): void {
    if (this.zooming() || !this.store.winner()) return;
    clearTimeout(this.timer);
    this.countdown.set(false);
    const card = this.winnerEl()?.nativeElement;
    if (!card || prefersReducedMotion(this.document)) {
      this.goToWinner();
      return;
    }
    this.zoomFrom = card.getBoundingClientRect();
    this.zooming.set(true);
  }

  /** Cancela a ida automática para os detalhes. */
  protected stay(): void {
    clearTimeout(this.timer);
    this.countdown.set(false);
  }

  private celebrate(): void {
    this.countdown.set(true);
    this.timer = setTimeout(() => this.openWinner(), CELEBRATE_MS);
    const view = this.document.defaultView;
    if (!view || prefersReducedMotion(this.document)) return;
    view.navigator.vibrate?.([40, 60, 40, 60, 120]);
    const deck = this.deckEl()?.nativeElement.getBoundingClientRect();
    const origin = deck
      ? { x: deck.left + deck.width / 2, y: deck.top + deck.height * 0.42 }
      : { x: view.innerWidth / 2, y: view.innerHeight / 2 };
    this.confetti.set(confetti(origin, view.innerWidth));
    setTimeout(() => this.confetti.set([]), 3400);
  }

  private zoom(el: HTMLElement): void {
    showPopover(el);
    const r = this.zoomFrom;
    if (!r || typeof el.animate !== 'function') {
      this.goToWinner();
      return;
    }
    const px = (n: number) => `${n}px`;
    el.animate(
      [
        { top: px(r.top), left: px(r.left), width: px(r.width), height: px(r.height), borderRadius: '32px' },
        { top: '0px', left: '0px', width: '100vw', height: '100dvh', borderRadius: '0px' },
      ],
      { duration: ZOOM_MS, easing: 'cubic-bezier(0.7, 0, 0.2, 1)', fill: 'forwards' },
    ).finished.then(
      () => this.goToWinner(),
      () => undefined, // página fechada no meio da animação
    );
  }

  private goToWinner(): void {
    const w = this.store.winner();
    if (w) this.router.navigate(titleLink(w));
  }

  protected async invite(): Promise<void> {
    const url = new URL(`match/${this.roomCode()}`, this.document.baseURI).href;
    const nav = this.document.defaultView?.navigator;
    try {
      if (nav?.share) {
        await nav.share({ title: 'Bora dar match no wannawatch?', text: `Sala ${this.roomCode()}`, url });
        return;
      }
      await nav?.clipboard.writeText(url);
      this.flash('Link da sala copiado');
    } catch {
      // cancelado ou sem permissão
    }
  }

  protected leave(): void {
    if (this.leaving()) return;
    this.leaving.set(true);
    this.store.leave().subscribe({
      next: () => this.router.navigate(['/match']),
      error: () => {
        this.leaving.set(false);
        this.flash('Não deu pra sair da sala agora');
      },
    });
  }

  /** Dono fechando a sala na sala de espera: some para todo mundo. */
  protected closeRoom(): void {
    if (this.leaving()) return;
    this.leaving.set(true);
    this.store.close().subscribe({
      next: () => this.router.navigate(['/match']),
      error: () => {
        this.leaving.set(false);
        this.flash('Não deu pra fechar a sala agora');
      },
    });
  }

  protected onKey(event: KeyboardEvent): void {
    if (this.store.waiting()) return; // na sala de espera não tem carta para votar
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const target = event.target;
    if (target instanceof Element && target.closest('input, textarea, select, [contenteditable="true"]')) return;
    const action = KEYS[event.key];
    if (!action) return;
    event.preventDefault();
    if (action === 'undo') this.undo();
    else this.act(action);
  }

  private flash(text: string): void {
    this.toast.set(text);
    setTimeout(() => this.toast.set(null), 2000);
  }
}

function showPopover(el: HTMLElement | undefined): void {
  if (el && typeof el.showPopover === 'function' && !el.matches(':popover-open')) el.showPopover();
}
