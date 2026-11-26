import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { AuthFrame } from '../../shared/ui/auth-frame';
import { Button } from '../../shared/ui/button';
import { Logo } from '../../shared/ui/logo';

/** Entrar com usuário (ou e-mail) e senha. Quem não terminou o cadastro segue para /cadastro. */
@Component({
  selector: 'ww-login-page',
  imports: [AuthFrame, Button, Logo, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ww-auth-frame art="entrar">
      <section aria-labelledby="entrar">
        <ww-logo class="brand" [size]="30" [mark]="true" />
        <h1 id="entrar">Que bom te ver de novo!</h1>
        <p class="lead">Entre para ver o que a galera curtiu e dar match no filme de hoje.</p>

        <form (submit)="submit($event)" novalidate>
          <label>
            <span>Usuário ou e-mail</span>
            <input
              name="username"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              required
              [value]="username()"
              (input)="username.set($any($event.target).value)"
            />
          </label>
          <label>
            <span>Senha</span>
            <input
              name="password"
              type="password"
              autocomplete="current-password"
              required
              [value]="password()"
              (input)="password.set($any($event.target).value)"
            />
          </label>
          @if (error(); as error) {
            <p class="error" role="alert">{{ error }}</p>
          }
          <button wwButton variant="primary" size="lg" type="submit" [disabled]="busy()">
            {{ busy() ? 'Entrando…' : 'Entrar' }}
          </button>
        </form>

        <p class="signup">
          Ainda não tem conta?
          <a routerLink="/cadastro" [queryParams]="volta() ? { volta: volta() } : {}"
            >Criar conta</a
          >
        </p>
      </section>
    </ww-auth-frame>
  `,
  styles: `
    section {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-3);
      width: min(100%, 360px);
      margin: 0 auto;
      text-align: center;
    }
    .brand {
      align-self: center;
    }
    h1 {
      margin-top: var(--ww-space-6);
      font-size: 32px;
      font-weight: 800;
      letter-spacing: var(--ww-tracking-display);
      line-height: 1.1;
    }
    .lead {
      color: var(--text-2);
    }
    form {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-4);
      margin-top: var(--ww-space-6);
      text-align: left;
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
    .signup {
      margin-top: var(--ww-space-6);
      color: var(--text-2);
      font-size: 14px;
    }
    .signup a {
      display: inline-flex;
      align-items: center;
      min-height: var(--ww-touch);
      color: var(--link);
      font-weight: 600;
    }
  `,
})
export class LoginPage {
  /** ?volta=/match/ABC123 — para onde ir depois de entrar */
  readonly volta = input<string>();

  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

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
      next: ({ account }) => {
        const back = safeReturn(this.volta());
        if (account.complete) this.router.navigateByUrl(back);
        // cadastro pela metade: continua de onde parou
        else
          this.router.navigate(['/cadastro'], { queryParams: back !== '/' ? { volta: back } : {} });
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(
          e.status === 401
            ? 'Usuário ou senha incorretos.'
            : 'Não deu pra entrar agora. O back-end está rodando?',
        );
      },
    });
  }
}

/** Só caminhos internos ("/..."), nunca outro site. */
export function safeReturn(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}
