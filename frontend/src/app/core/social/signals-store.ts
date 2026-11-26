import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { InteractionSource, SignalKind, TitleSignals } from '../api/models';
import { SocialApi } from '../api/social-api';
import { AuthStore } from '../auth/auth-store';

/** O back aceita até 100 ids por chamada. */
const BATCH = 100;

/**
 * Passa / talvez / já vi / salvar de quem está logado, por título. Mesmo esquema do
 * `LikesStore`: as telas pedem `ensure(ids)`, os pedidos do mesmo instante viram uma chamada só,
 * e marcar/desmarcar muda a tela na hora e desfaz se o back recusar.
 */
@Injectable({ providedIn: 'root' })
export class SignalsStore {
  private readonly api = inject(SocialApi);
  private readonly auth = inject(AuthStore);

  private readonly entries = signal<ReadonlyMap<number, ReadonlySet<SignalKind>>>(new Map());
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

  has(titleId: number, kind: SignalKind): boolean {
    return this.entries().get(titleId)?.has(kind) ?? false;
  }

  /** Busca os sinais dos títulos que ainda não conhece. */
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

  set(titleId: number, kind: SignalKind, on: boolean, source?: InteractionSource): void {
    if (this.has(titleId, kind) === on) return;
    this.put(titleId, kind, on);
    const call = on
      ? this.api.putSignal(titleId, kind, source)
      : this.api.deleteSignal(titleId, kind, source);
    call.subscribe({ error: () => this.put(titleId, kind, !on) });
  }

  toggle(titleId: number, kind: SignalKind, source?: InteractionSource): void {
    this.set(titleId, kind, !this.has(titleId, kind), source);
  }

  private flush(): void {
    this.flushScheduled = false;
    const ids = [...this.queue];
    this.queue.clear();
    for (let i = 0; i < ids.length; i += BATCH) {
      const chunk = ids.slice(i, i + BATCH);
      this.api.signals(chunk).subscribe({
        next: (items) => this.remember(items),
        // deixa tentar de novo da próxima vez que a tela pedir
        error: () => chunk.forEach((id) => this.requested.delete(id)),
      });
    }
  }

  private remember(items: TitleSignals[]): void {
    if (!items.length) return;
    this.entries.update((m) => {
      const next = new Map(m);
      for (const i of items) next.set(i.titleId, new Set(i.kinds));
      return next;
    });
  }

  private put(titleId: number, kind: SignalKind, on: boolean): void {
    this.entries.update((m) => {
      const kinds = new Set(m.get(titleId));
      if (on) kinds.add(kind);
      else kinds.delete(kind);
      return new Map(m).set(titleId, kinds);
    });
  }
}
