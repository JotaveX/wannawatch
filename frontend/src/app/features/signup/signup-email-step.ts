import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { SocialApi } from '../../core/api/social-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { problemDetail } from './signup-errors';

/** O back aceita um reenvio por minuto. */
export const RESEND_SECONDS = 60;
const LENGTH = 6;

/** Etapa 3: o código de 6 dígitos que foi para o e-mail quando a conta foi criada. */
@Component({
  selector: 'ww-signup-email-step',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './signup-form.css',
  template: `
    <h1 #heading tabindex="-1">Confirme seu e-mail</h1>
    <p class="lead">
      Enviamos um código para <strong>{{ email() }}</strong>
    </p>

    <form (submit)="submit($event)" novalidate>
      <div
        class="boxes"
        role="group"
        aria-label="Código de 6 dígitos"
        [attr.aria-describedby]="error() ? 'erro-codigo' : null"
      >
        @for (digit of digits(); track $index; let i = $index) {
          <input
            #box
            [name]="'digit-' + i"
            class="box"
            [class.filled]="!!digit"
            inputmode="numeric"
            pattern="[0-9]*"
            [attr.autocomplete]="i === 0 ? 'one-time-code' : 'off'"
            [attr.aria-label]="'Dígito ' + (i + 1) + ' de 6'"
            [attr.aria-invalid]="!!error()"
            [value]="digit"
            (input)="onInput(i, $event)"
            (keydown)="onKeydown(i, $event)"
            (paste)="onPaste(i, $event)"
            (focus)="$any($event.target).select()"
          />
        }
      </div>
      @if (error(); as error) {
        <p class="error alert" id="erro-codigo" role="alert">{{ error }}</p>
      }
      <button wwButton variant="primary" size="lg" type="submit" [disabled]="busy()">
        {{ busy() ? 'Conferindo…' : 'Confirmar' }}
      </button>
    </form>

    <p class="resend" aria-live="polite">
      @if (notice()) {
        {{ notice() }}
      } @else if (wait() > 0) {
        Reenviar em {{ wait() }}s
      } @else {
        Não recebeu?
        <button type="button" class="link" [disabled]="sending()" (click)="resend()">
          {{ sending() ? 'Enviando…' : 'Reenviar código' }}
        </button>
      }
    </p>
  `,
  styles: `
    .boxes {
      display: flex;
      gap: var(--ww-space-2);
    }
    .box {
      flex: 1;
      min-width: 0;
      max-width: 56px;
      height: 60px;
      padding: 0;
      text-align: center;
      font-size: 26px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      caret-color: var(--ww-pink-strong);
    }
    .box.filled {
      border-color: var(--text);
    }
    form > button {
      width: 100%;
    }
    .resend {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      min-height: var(--ww-touch);
      color: var(--text-2);
      font-size: 14px;
    }
    .link {
      min-height: var(--ww-touch);
      padding: 0 4px;
      border: 0;
      background: none;
      color: var(--link);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    .link:hover:not(:disabled) {
      text-decoration: underline;
    }
    .link:disabled {
      opacity: 0.6;
      cursor: default;
    }
    .link:focus-visible {
      outline: 2px solid var(--focus-ring);
      outline-offset: 2px;
      border-radius: 4px;
    }
  `,
})
export class SignupEmailStep {
  private readonly api = inject(SocialApi);
  private readonly auth = inject(AuthStore);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');
  private readonly boxes = viewChildren<ElementRef<HTMLInputElement>>('box');

  protected readonly email = computed(() => this.auth.account()?.email ?? 'o seu e-mail');
  protected readonly digits = signal<string[]>(Array(LENGTH).fill(''));
  private readonly code = computed(() => this.digits().join(''));
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly notice = signal('');
  /** segundos até poder pedir outro código */
  protected readonly wait = signal(0);
  private timer: ReturnType<typeof setInterval> | undefined;
  private noticeTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
    inject(DestroyRef).onDestroy(() => {
      clearInterval(this.timer);
      clearTimeout(this.noticeTimer);
    });
  }

  protected onInput(i: number, event: Event): void {
    const target = event.target as HTMLInputElement;
    const typed = target.value.replace(/\D/g, '');
    // várias de uma vez: autopreenchimento do código ou colagem que não passou pelo (paste)
    if (typed.length > 1) {
      this.fill(typed.length >= LENGTH ? 0 : i, typed);
      return;
    }
    target.value = typed;
    this.setDigit(i, typed);
    if (typed) this.afterTyping(i + 1);
  }

  protected onKeydown(i: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.digits()[i] && i > 0) {
      event.preventDefault();
      this.setDigit(i - 1, '');
      this.focus(i - 1);
    } else if (event.key === 'ArrowLeft' && i > 0) {
      event.preventDefault();
      this.focus(i - 1);
    } else if (event.key === 'ArrowRight' && i < LENGTH - 1) {
      event.preventDefault();
      this.focus(i + 1);
    }
  }

  protected onPaste(i: number, event: ClipboardEvent): void {
    const pasted = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '');
    if (!pasted) return;
    event.preventDefault();
    this.fill(pasted.length >= LENGTH ? 0 : i, pasted);
  }

  protected submit(event?: Event): void {
    event?.preventDefault();
    if (this.busy()) return;
    const code = this.code();
    if (code.length !== LENGTH) {
      this.error.set('O código tem 6 números.');
      this.focus(this.digits().findIndex((d) => !d));
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
    this.api.resendEmail().subscribe({
      next: () => {
        this.sending.set(false);
        this.error.set(null);
        this.digits.set(Array(LENGTH).fill(''));
        this.flash('Código novo enviado.');
        this.countdown();
        this.focus(0);
      },
      error: (e: HttpErrorResponse) => {
        this.sending.set(false);
        this.flash(problemDetail(e) ?? 'Não deu pra reenviar agora.');
        if (e.status === 400) this.countdown();
      },
    });
  }

  private setDigit(i: number, value: string): void {
    this.digits.update((d) => d.map((old, k) => (k === i ? value : old)));
  }

  private fill(start: number, chars: string): void {
    const next = [...this.digits()];
    let last = start;
    for (let k = 0; k < chars.length && start + k < LENGTH; k++) {
      next[start + k] = chars[k];
      last = start + k;
    }
    this.digits.set(next);
    // o [value] não atualiza a caixa onde a pessoa digitou se o dígito dela não mudou
    this.boxes().forEach((box, k) => (box.nativeElement.value = next[k]));
    this.afterTyping(last + 1);
  }

  /** vai para a próxima caixa ou, com o código completo, já confere */
  private afterTyping(next: number): void {
    this.error.set(null);
    if (this.code().length === LENGTH) {
      this.boxes()[LENGTH - 1]?.nativeElement.blur();
      this.submit();
    } else if (next < LENGTH) {
      this.focus(next);
    }
  }

  private focus(i: number): void {
    this.boxes()[Math.max(0, i)]?.nativeElement.focus();
  }

  /** mostra o aviso no lugar do link por alguns segundos */
  private flash(text: string): void {
    clearTimeout(this.noticeTimer);
    this.notice.set(text);
    this.noticeTimer = setTimeout(() => this.notice.set(''), 4000);
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
