import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { UserRef } from '../api/models';
import { SocialApi } from '../api/social-api';

const STORAGE_KEY = 'ww.session';

interface Session {
  token: string;
  user: UserRef;
}

/**
 * Quem está logado. A sessão (token + usuário) fica no localStorage para sobreviver ao recarregar;
 * se o back recusar o token (401), o authInterceptor chama clear() e manda para o login.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(SocialApi);
  private readonly session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly loggedIn = computed(() => this.session() !== null);
  readonly initials = computed(() => initialsOf(this.user()?.displayName ?? ''));

  token(): string | null {
    return this.session()?.token ?? null;
  }

  login(username: string, password: string): Observable<unknown> {
    return this.api.login(username, password).pipe(tap(({ token, user }) => this.save({ token, user })));
  }

  /** Encerra no back (sem esperar) e esquece a sessão aqui. */
  logout(): void {
    if (this.token()) this.api.logout().subscribe({ error: () => undefined });
    this.clear();
  }

  /** Atualiza o usuário guardado na sessão (depois de editar o perfil). */
  updateUser(user: UserRef): void {
    const session = this.session();
    if (session) this.save({ ...session, user });
  }

  clear(): void {
    this.session.set(null);
    storage()?.removeItem(STORAGE_KEY);
  }

  private save(session: Session): void {
    this.session.set(session);
    storage()?.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

/** "Bia Rocha" → "BR"; "Jotave" → "JO". */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts.at(-1)![0]).toUpperCase();
}

function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

function readSession(): Session | null {
  try {
    const raw = storage()?.getItem(STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as Session) : null;
    return parsed?.token && parsed.user ? parsed : null;
  } catch {
    return null;
  }
}
