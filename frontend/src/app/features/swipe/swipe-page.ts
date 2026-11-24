import { ChangeDetectionStrategy, Component, computed, inject, viewChildren } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SegmentOption } from '../../shared/ui/segmented';
import { length, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { SocialMock, ViewMode } from '../../mocks/social.mock';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { Logo } from '../../shared/ui/logo';
import { Segmented } from '../../shared/ui/segmented';
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
  imports: [RouterLink, SwipeCard, Button, Icon, IconButton, Logo, Segmented],
  providers: [SwipeStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
  templateUrl: './swipe-page.html',
  styleUrl: './swipe-page.css',
})
export class SwipePage {
  protected readonly store = inject(SwipeStore);
  protected readonly social = inject(SocialMock);
  private readonly cards = viewChildren(SwipeCard);

  protected readonly modes: SegmentOption<ViewMode>[] = [
    { value: 'group', label: 'Grupo' },
    { value: 'solo', label: 'Solo' },
  ];

  /** carta de baixo primeiro, para a de cima ficar por cima no DOM */
  protected readonly stack = computed(() =>
    [this.store.next(), this.store.current()].filter((c) => c !== null),
  );

  protected readonly status = computed(() => {
    const last = this.store.last();
    if (last) return { dot: last.decision, text: `${LAST_TEXT[last.decision]} ${last.card.title}` };
    return {
      dot: null,
      text: this.social.isGroup()
        ? `Votando com ${this.social.group.name} · ${this.social.group.members} pessoas`
        : 'Recomendações só pra você',
    };
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
