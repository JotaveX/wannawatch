import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal, viewChildren } from '@angular/core';
import { RouterLink } from '@angular/router';
import { length, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { SocialMock } from '../../mocks/social.mock';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { ReelItem } from './reel-item';
import { ReelsStore } from './reels-store';
import { LikedBy } from './liked-by';

const KEYS: Record<string, 'prev' | 'next' | 'like' | 'save'> = {
  ArrowUp: 'prev',
  k: 'prev',
  K: 'prev',
  ArrowDown: 'next',
  j: 'next',
  J: 'next',
  l: 'like',
  L: 'like',
  s: 'save',
  S: 'save',
};

/** Feed vertical de sinopses (estilo TikTok), só individual. */
@Component({
  selector: 'ww-reels-page',
  imports: [RouterLink, ReelItem, Button, Icon, IconButton, LikedBy],
  providers: [ReelsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
  templateUrl: './reels-page.html',
  styleUrl: './reels-page.css',
})
export class ReelsPage {
  protected readonly store = inject(ReelsStore);
  protected readonly social = inject(SocialMock);
  private readonly document = inject(DOCUMENT);
  private readonly reels = viewChildren(ReelItem, { read: ElementRef });

  /** "quero ver" e "salvar" só valem nesta tela (sem persistência por enquanto) */
  protected readonly liked = signal<ReadonlySet<number>>(new Set());
  protected readonly saved = signal<ReadonlySet<number>>(new Set());
  protected readonly active = signal(0);
  protected readonly toast = signal<string | null>(null);

  protected readonly current = computed(() => this.store.items()[this.active()] ?? null);
  protected readonly facts = computed(() => {
    const c = this.current();
    return c ? [typeLabel(c), c.year ? String(c.year) : null, length(c)].filter(Boolean).join(' · ') : '';
  });
  protected readonly titleLink = titleLink;

  constructor() {
    this.store.load();
  }

  protected onVisible(index: number): void {
    this.active.set(index);
    this.store.reached(index);
  }

  protected toggle(set: typeof this.liked, id: number): void {
    set.update((s) => {
      const next = new Set(s);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  protected go(delta: number): void {
    const target = this.active() + delta;
    const el = this.reels()[target]?.nativeElement as HTMLElement | undefined;
    if (!el) return;
    const reduced = matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    el.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
    // sem IntersectionObserver (ex.: testes), atualiza na hora
    if (typeof IntersectionObserver === 'undefined') this.onVisible(target);
  }

  protected async share(id: number): Promise<void> {
    const card = this.store.items().find((c) => c.id === id);
    if (!card) return;
    const url = new URL(titleLink(card).join('/'), this.document.baseURI).href;
    const nav = this.document.defaultView?.navigator;
    try {
      if (nav?.share) {
        await nav.share({ title: card.title, url });
        return;
      }
      await nav?.clipboard.writeText(url);
      this.flash('Link copiado');
    } catch {
      // cancelado pelo usuário ou sem permissão: não faz nada
    }
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const target = event.target;
    if (target instanceof Element && target.closest('input, textarea, select, [contenteditable="true"]')) return;
    const action = KEYS[event.key];
    const card = this.current();
    if (!action || !card) return;
    event.preventDefault();
    if (action === 'prev') this.go(-1);
    else if (action === 'next') this.go(1);
    else if (action === 'like') this.toggle(this.liked, card.id);
    else this.toggle(this.saved, card.id);
  }

  private flash(text: string): void {
    this.toast.set(text);
    setTimeout(() => this.toast.set(null), 2000);
  }
}
