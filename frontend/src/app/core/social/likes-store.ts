import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { InteractionSource, TitleLikes, UserRef } from '../api/models';
import { SocialApi } from '../api/social-api';
import { AuthStore } from '../auth/auth-store';

/** O back aceita até 100 ids por chamada. */
const BATCH = 100;

/**
 * Curtidas ("quero ver") por título: as minhas e as dos amigos. Ainda não há lista de amigos,
 * então "amigos" são todos os outros usuários.
 *
 * As telas pedem `ensure(ids)` para os títulos que vão mostrar; os pedidos do mesmo instante
 * viram uma chamada só. Curtir/descurtir muda a tela na hora e desfaz se o back recusar.
 */
@Injectable({ providedIn: 'root' })
export class LikesStore {
  private readonly api = inject(SocialApi);
  private readonly auth = inject(AuthStore);

  private readonly entries = signal<ReadonlyMap<number, TitleLikes>>(new Map());
  private readonly requested = new Set<number>();
  private queue = new Set<number>();
  private flushScheduled = false;

  constructor() {
    // trocou de usuário (ou saiu): o que estava em cache era de outra pessoa
    let userId = this.auth.user()?.id;
    effect(() => {
      const current = this.auth.user()?.id;
      if (current === userId) return;
      userId = current;
      untracked(() => {
        this.entries.set(new Map());
        this.requested.clear();
        this.queue.clear();
      });
    });
  }

  get(titleId: number): TitleLikes | null {
    return this.entries().get(titleId) ?? null;
  }

  likedByMe(titleId: number): boolean {
    return this.get(titleId)?.likedByMe ?? false;
  }

  friends(titleId: number): UserRef[] {
    return this.get(titleId)?.friends ?? [];
  }

  /** Guarda o que já veio pronto do back (ex.: a fileira "Seus amigos curtiram"). */
  remember(items: TitleLikes[]): void {
    if (!items.length) return;
    for (const i of items) this.requested.add(i.titleId);
    this.entries.update((m) => {
      const next = new Map(m);
      for (const i of items) next.set(i.titleId, i);
      return next;
    });
  }

  /** Busca as curtidas dos títulos que ainda não conhece. */
  ensure(titleIds: readonly number[]): void {
    if (!this.auth.loggedIn()) return;
    for (const id of titleIds) {
      if (!this.requested.has(id)) {
        this.requested.add(id);
        this.queue.add(id);
      }
    }
    if (!this.queue.size || this.flushScheduled) return;
    this.flushScheduled = true;
    queueMicrotask(() => this.flush());
  }

  setLiked(titleId: number, liked: boolean, source?: InteractionSource): void {
    const before = this.get(titleId);
    if ((before?.likedByMe ?? false) === liked) return;
    this.put({ titleId, likedByMe: liked, friends: before?.friends ?? [] });
    const call = liked ? this.api.like(titleId, source) : this.api.unlike(titleId, source);
    call.subscribe({
      error: () => this.put(before ?? { titleId, likedByMe: !liked, friends: [] }),
    });
  }

  toggle(titleId: number, source?: InteractionSource): void {
    this.setLiked(titleId, !this.likedByMe(titleId), source);
  }

  private flush(): void {
    this.flushScheduled = false;
    const ids = [...this.queue];
    this.queue.clear();
    for (let i = 0; i < ids.length; i += BATCH) {
      const chunk = ids.slice(i, i + BATCH);
      this.api.likes(chunk).subscribe({
        next: (items) => this.remember(items),
        // deixa tentar de novo da próxima vez que a tela pedir
        error: () => chunk.forEach((id) => this.requested.delete(id)),
      });
    }
  }

  private put(item: TitleLikes): void {
    this.entries.update((m) => new Map(m).set(item.titleId, item));
  }
}

/** "Bia curtiu" · "Bia e Caio curtiram" · "Bia e mais 2 curtiram" */
export function likedByLabel(friends: readonly UserRef[]): string | null {
  const first = (u: UserRef) => u.displayName.split(' ')[0];
  if (friends.length === 0) return null;
  if (friends.length === 1) return `${first(friends[0])} curtiu`;
  if (friends.length === 2) return `${first(friends[0])} e ${first(friends[1])} curtiram`;
  return `${first(friends[0])} e mais ${friends.length - 1} curtiram`;
}
