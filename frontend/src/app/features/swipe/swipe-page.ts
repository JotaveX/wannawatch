import { ChangeDetectionStrategy, Component, computed, inject, viewChildren } from '@angular/core';
import { RouterLink } from '@angular/router';
import { length, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { LikesStore, likedByLabel } from '../../core/social/likes-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { Logo } from '../../shared/ui/logo';
import { Mascot } from '../../shared/ui/mascot';
import { SwipeCard } from './swipe-card';
import { Decision, SwipeStore } from './swipe-store';

const KEYS: Record<string, Decision | 'undo'> = {
  ArrowLeft: 'pass',
  ArrowRight: 'like',
  ArrowUp: 'maybe',
  ArrowDown: 'seen',
  z: 'undo',
  Z: 'undo',
  Backspace: 'undo',
};

const LAST_TEXT: Record<Decision, string> = {
  pass: 'Passou',
  maybe: 'Talvez:',
  like: 'Você quer ver',
  seen: 'Já viu',
};

@Component({
  selector: 'ww-swipe-page',
  imports: [RouterLink, SwipeCard, Button, Icon, IconButton, Logo, Mascot],
  providers: [SwipeStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
  templateUrl: './swipe-page.html',
  styleUrl: './swipe-page.css',
})
export class SwipePage {
  protected readonly store = inject(SwipeStore);
  private readonly likes = inject(LikesStore);
  private readonly cards = viewChildren(SwipeCard);

  /** carta de baixo primeiro, para a de cima ficar por cima no DOM */
  protected readonly stack = computed(() =>
    [this.store.next(), this.store.current()].filter((c) => c !== null),
  );

  protected readonly status = computed(() => {
    const last = this.store.last();
    if (last) return { dot: last.decision, text: `${LAST_TEXT[last.decision]} ${last.card.title}` };
    return { dot: null, text: 'Seu "quero ver" aparece para os amigos' };
  });

  protected readonly currentLikedBy = computed(() => {
    const c = this.store.current();
    return c ? (likedByLabel(this.likes.friends(c.id)) ?? c.reason?.text ?? null) : null;
  });

  protected readonly currentFacts = computed(() => {
    const c = this.store.current();
    if (!c) return [];
    return [typeLabel(c), c.year ? String(c.year) : null, length(c), c.ageRating ? `Classificação ${c.ageRating}` : null].filter(
      (p): p is string => !!p,
    );
  });
  protected readonly titleLink = titleLink;

  constructor() {
    this.store.start();
  }

  protected act(decision: Decision): void {
    if (!this.store.current()) return;
    const top = this.cards().find((c) => c.interactive());
    if (top?.leaving()) return; // ainda saindo da tela
    if (top) top.fling(decision);
    else this.store.decide(decision);
  }

  protected undo(): void {
    if (this.cards().some((c) => c.leaving())) return;
    this.store.undo();
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const target = event.target;
    if (target instanceof Element && target.closest('input, textarea, select, [contenteditable="true"]')) return;
    const action = KEYS[event.key];
    if (!action) return;
    event.preventDefault();
    if (action === 'undo') this.undo();
    else this.act(action);
  }
}
