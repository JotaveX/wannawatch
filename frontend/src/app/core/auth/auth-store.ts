import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AccountStatus, LoginResponse, RegisterRequest, UserRef } from '../api/models';
import { SocialApi } from '../api/social-api';

const STORAGE_KEY = 'ww.session';

interface Session {
  token: string;
  user: UserRef;
  /** ausente nas sessões salvas antes do cadastro existir (até o refresh() trazer) */
  account?: AccountStatus;
}

/** Etapa do cadastro que falta, na ordem: conta → títulos → e-mail. */
export type SignupStep = 'account' | 'titles' | 'email' | 'done';

/**
 * Quem está logado. A sessão (token + usuário + etapa do cadastro) fica no localStorage para
 * sobreviver ao recarregar; se o back recusar o token (401), o authInterceptor chama clear() e
 * manda para o login.
 *
 * Sem login dá para ver o catálogo (Início, detalhes, busca); Swipe, Reels, Match e perfil pedem
 * conta. Quem entrou mas não terminou o cadastro é levado de volta para /cadastro.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(SocialApi);
  private readonly session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly loggedIn = computed(() => this.session() !== null);
  readonly initials = computed(() => initialsOf(this.user()?.displayName ?? ''));
  readonly account = computed(() => this.session()?.account ?? null);
  /** Só esconde/mostra o menu de admin; quem decide de verdade é o back (403). */
  readonly isAdmin = computed(() => this.session()?.account?.admin === true);

  /** Sem `account` (sessão antiga) conta como feito: o refresh() corrige se não for. */
  readonly signupStep = computed<SignupStep>(() => {
    const session = this.session();
    if (!session) return 'account';
    const account = session.account;
    if (!account) return 'done';
    if (!account.titlesPicked) return 'titles';
    if (!account.emailVerified) return 'email';
    return 'done';
  });

  /** Logado e com o cadastro terminado. */
  readonly ready = computed(() => this.signupStep() === 'done');

  token(): string | null {
    return this.session()?.token ?? null;
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.api.login(username, password).pipe(tap((r) => this.save(r)));
  }

  /** Etapa 1 do cadastro: a conta já sai logada. */
  register(body: RegisterRequest): Observable<LoginResponse> {
    return this.api.register(body).pipe(tap((r) => this.save(r)));
  }

  /** Busca de novo no back quem sou e em que etapa estou (ao abrir o app). */
  refresh(): void {
    if (!this.token()) return;
    this.api.account().subscribe({
      next: ({ user, account }) => {
        const session = this.session();
        if (session) this.save({ ...session, user, account });
      },
      // 401 o interceptor já trata; erro de rede mantém o que estava salvo
      error: () => undefined,
    });
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

  /** Atualiza a etapa do cadastro (depois de escolher os títulos ou confirmar o e-mail). */
  updateAccount(account: AccountStatus): void {
    const session = this.session();
    if (session) this.save({ ...session, account });
  }

  clear(): void {
    this.session.set(null);
    storage()?.removeItem(STORAGE_KEY);
  }

  private save(session: Session): void {
    const { token, user, account } = session;
    const clean: Session = { token, user, account };
    this.session.set(clean);
    storage()?.setItem(STORAGE_KEY, JSON.stringify(clean));
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
