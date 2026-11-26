import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChildren,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { length, typeLabel } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { LikesStore } from '../../core/social/likes-store';
import { SignalsStore } from '../../core/social/signals-store';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { IconButton } from '../../shared/ui/icon-button';
import { Mascot } from '../../shared/ui/mascot';
import { ReelItem } from './reel-item';
import { ReelsStore } from './reels-store';
import { LikedBy } from './liked-by';

const KEYS: Record<string, 'prev' | 'next' | 'like' | 'save' | 'mute' | 'pause'> = {
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
  m: 'mute',
  M: 'mute',
  p: 'pause',
  P: 'pause',
};

/** Feed vertical (estilo TikTok), só individual: vídeo quando o título tem um aprovado, senão a sinopse. */
@Component({
  selector: 'ww-reels-page',
  imports: [RouterLink, ReelItem, Button, Icon, IconButton, Mascot, LikedBy],
  providers: [ReelsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
  templateUrl: './reels-page.html',
  styleUrl: './reels-page.css',
})
export class ReelsPage {
  protected readonly store = inject(ReelsStore);
  protected readonly likes = inject(LikesStore);
  protected readonly signals = inject(SignalsStore);
  private readonly document = inject(DOCUMENT);
  private readonly reels = viewChildren(ReelItem, { read: ElementRef });
  private readonly reelItems = viewChildren(ReelItem);

  protected readonly active = signal(0);
  protected readonly toast = signal<string | null>(null);
  /** começa sem som (o navegador só deixa tocar sozinho assim); vale para o feed todo */
  protected readonly muted = signal(true);
  /** volume de 0 a 100, também do feed todo */
  protected readonly volume = signal(100);

  protected readonly current = computed(() => this.store.items()[this.active()] ?? null);
  protected readonly facts = computed(() => {
    const c = this.current();
    return c
      ? [typeLabel(c), c.year ? String(c.year) : null, length(c)].filter(Boolean).join(' · ')
      : '';
  });
  protected readonly titleLink = titleLink;

  constructor() {
    this.store.load();
    // quem curtiu e o que eu salvei, para cada reel que chegou
    effect(() => {
      const ids = this.store.items().map((c) => c.id);
      this.likes.ensure(ids);
      this.signals.ensure(ids);
    });
  }

  protected onVisible(index: number): void {
    this.active.set(index);
    this.store.reached(index);
  }

  protected toggleLike(id: number): void {
    this.likes.toggle(id, 'REELS');
  }

  protected toggleSaved(id: number): void {
    this.signals.toggle(id, 'SAVED', 'REELS');
  }

  protected toggleMute(): void {
    // ligar o som com o volume no zero não adiantaria: volta para a metade
    if (this.muted() && !this.volume()) this.volume.set(50);
    this.muted.update((m) => !m);
  }

  /** Mexer no volume liga o som; levar ao zero é o mesmo que tirar o som. */
  protected setVolume(value: number): void {
    this.volume.set(value);
    this.muted.set(value === 0);
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
    if (
      target instanceof Element &&
      target.closest('input, textarea, select, [contenteditable="true"]')
    )
      return;
    const action = KEYS[event.key];
    const card = this.current();
    if (!action || !card) return;
    event.preventDefault();
    if (action === 'prev') this.go(-1);
    else if (action === 'next') this.go(1);
    else if (action === 'like') this.toggleLike(card.id);
    else if (action === 'save') this.toggleSaved(card.id);
    else if (!card.video) return;
    else if (action === 'mute') this.toggleMute();
    else this.reelItems()[this.active()]?.togglePause();
  }

  private flash(text: string): void {
    this.toast.set(text);
    setTimeout(() => this.toast.set(null), 2000);
  }
}
