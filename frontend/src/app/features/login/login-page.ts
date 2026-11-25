import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, isDevMode, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { Logo } from '../../shared/ui/logo';

/** Não há cadastro: as contas são criadas pela migration do back (veja o README). */
@Component({
  selector: 'ww-login-page',
  imports: [Button, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="entrar">
      <ww-logo [size]="34" [mark]="true" />
      <h1 id="entrar">Entrar</h1>
      <p class="lead">Veja o que a galera curtiu e dê match no filme de hoje.</p>

      <form (submit)="submit($event)" novalidate>
        <label>
          <span>Usuário</span>
          <input name="username" autocomplete="username" autocapitalize="none" spellcheck="false" required
                 [value]="username()" (input)="username.set($any($event.target).value)" />
        </label>
        <label>
          <span>Senha</span>
          <input name="password" type="password" autocomplete="current-password" required
                 [value]="password()" (input)="password.set($any($event.target).value)" />
        </label>
        @if (error(); as error) {
          <p class="error" role="alert">{{ error }}</p>
        }
        <button wwButton variant="primary" size="lg" type="submit" [disabled]="busy()">
          {{ busy() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>

      @if (dev) {
        <p class="hint">Contas de teste: <strong>jotave</strong>, <strong>bia</strong> e <strong>caio</strong>, senha <strong>wannawatch</strong>.</p>
      }
    </section>
  `,
  styles: `
    :host {
      display: grid;
      place-items: center;
      min-height: 100dvh;
      padding: var(--ww-space-8) var(--ww-page-x);
    }
    section {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-3);
      width: min(100%, 380px);
    }
    h1 {
      margin-top: var(--ww-space-6);
      font-size: 34px;
      font-weight: 800;
      letter-spacing: var(--ww-tracking-display);
    }
    .lead {
      color: var(--text-2);
    }
    form {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-4);
      margin-top: var(--ww-space-4);
    }
    label {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
    }
    input {
      min-height: 48px;
      padding: 0 var(--ww-space-4);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-sm);
      background: var(--surface);
      color: var(--text);
      font: inherit;
      font-size: 16px;
      font-weight: 400;
    }
    input:focus-visible {
      outline: 2px solid var(--focus-ring);
      outline-offset: 1px;
    }
    .error {
      color: var(--ww-pink-strong);
      font-size: 14px;
      font-weight: 600;
    }
    button {
      margin-top: var(--ww-space-2);
    }
    .hint {
      margin-top: var(--ww-space-4);
      color: var(--text-2);
      font-size: 13px;
    }
  `,
})
export class LoginPage {
  /** ?volta=/match/ABC123 — para onde ir depois de entrar */
  readonly volta = input<string>();

  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  protected readonly dev = isDevMode();

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.busy()) return;
    if (!this.username().trim() || !this.password()) {
      this.error.set('Preencha usuário e senha.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username().trim(), this.password()).subscribe({
      next: () => this.router.navigateByUrl(safeReturn(this.volta())),
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(e.status === 401 ? 'Usuário ou senha incorretos.' : 'Não deu pra entrar agora. O back-end está rodando?');
      },
    });
  }
}

/** Só caminhos internos ("/..."), nunca outro site. */
export function safeReturn(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}
