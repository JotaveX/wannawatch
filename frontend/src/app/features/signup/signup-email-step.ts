import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { SocialApi } from '../../core/api/social-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { problemDetail } from './signup-errors';

/** O back aceita um reenvio por minuto. */
export const RESEND_SECONDS = 60;

/** Etapa 3: o código de 6 dígitos que foi para o e-mail quando a conta foi criada. */
@Component({
  selector: 'ww-signup-email-step',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './signup-form.css',
  template: `
    <h1 #heading tabindex="-1">Confirme seu e-mail</h1>
    <p class="lead">
      Mandamos um código de 6 dígitos para <strong>{{ email() }}</strong>. Ele vale por 30 minutos.
    </p>

    <form (submit)="submit($event)" novalidate>
      <label>
        <span>Código</span>
        <input
          name="code"
          class="code"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="7"
          required
          [attr.aria-invalid]="!!error()"
          [attr.aria-describedby]="error() ? 'erro-codigo' : null"
          [value]="code()"
          (input)="code.set($any($event.target).value)"
        />
      </label>
      @if (error(); as error) {
        <p class="error alert" id="erro-codigo" role="alert">{{ error }}</p>
      }
      <button wwButton variant="primary" size="lg" type="submit" [disabled]="busy()">
        {{ busy() ? 'Conferindo…' : 'Confirmar e começar' }}
      </button>
    </form>

    <div class="resend">
      <button
        wwButton
        variant="outline"
        type="button"
        [disabled]="wait() > 0 || sending()"
        (click)="resend()"
      >
        {{
          wait() > 0 ? 'Reenviar em ' + wait() + 's' : sending() ? 'Enviando…' : 'Reenviar código'
        }}
      </button>
      <p class="help" aria-live="polite">{{ notice() }}</p>
    </div>

    @if (dev()) {
      <p class="help">
        Em desenvolvimento o e-mail não sai de verdade: ele chega no Mailpit, em
        <a href="http://localhost:8025" target="_blank" rel="noopener">localhost:8025</a>.
      </p>
    }
  `,
  styles: `
    .code {
      max-width: 200px;
      font-size: 24px;
      font-weight: 700;
      letter-spacing: 0.3em;
      font-variant-numeric: tabular-nums;
    }
    .resend {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--ww-space-3);
      margin-top: var(--ww-space-2);
    }
    .help a {
      color: var(--link);
    }
  `,
})
export class SignupEmailStep {
  readonly dev = input(false);

  private readonly api = inject(SocialApi);
  private readonly auth = inject(AuthStore);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  protected readonly email = computed(() => this.auth.account()?.email ?? 'o seu e-mail');
  protected readonly code = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly notice = signal('');
  /** segundos até poder pedir outro código */
  protected readonly wait = signal(0);
  private timer: ReturnType<typeof setInterval> | undefined;

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.busy()) return;
    const code = this.code().replace(/\D/g, '');
    if (code.length !== 6) {
      this.error.set('O código tem 6 números.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.verifyEmail(code).subscribe({
      // e-mail confirmado: a página leva para o app
      next: (account) => this.auth.updateAccount(account),
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(
          problemDetail(e) ?? 'Não deu pra conferir agora. Tente de novo em instantes.',
        );
      },
    });
  }

  protected resend(): void {
    if (this.wait() > 0 || this.sending()) return;
    this.sending.set(true);
    this.notice.set('');
    this.api.resendEmail().subscribe({
      next: () => {
        this.sending.set(false);
        this.error.set(null);
        this.notice.set('Código novo enviado. O anterior não vale mais.');
        this.countdown();
      },
      error: (e: HttpErrorResponse) => {
        this.sending.set(false);
        this.notice.set(problemDetail(e) ?? 'Não deu pra reenviar agora.');
        if (e.status === 400) this.countdown();
      },
    });
  }

  private countdown(): void {
    clearInterval(this.timer);
    this.wait.set(RESEND_SECONDS);
    this.timer = setInterval(() => {
      this.wait.update((s) => Math.max(0, s - 1));
      if (this.wait() === 0) clearInterval(this.timer);
    }, 1000);
  }
}
