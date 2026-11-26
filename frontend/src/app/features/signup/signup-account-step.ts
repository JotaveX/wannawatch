import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { AuthStore } from '../../core/auth/auth-store';
import { Button } from '../../shared/ui/button';
import { problemDetail } from './signup-errors';

type Field = 'displayName' | 'username' | 'email' | 'password';

/** Mesmas regras do SignupService no back, para avisar antes de enviar. */
export function accountErrors(v: Record<Field, string>): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {};
  if (!v.displayName.trim()) errors.displayName = 'Diga como quer ser chamado.';
  else if (v.displayName.trim().length > 40) errors.displayName = 'O nome pode ter até 40 letras.';
  if (!/^[a-z0-9][a-z0-9_.]{2,19}$/.test(v.username.trim().toLowerCase()))
    errors.username = 'De 3 a 20 caracteres: letras minúsculas, números, _ ou ponto.';
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.email.trim()))
    errors.email = 'Esse e-mail não parece certo.';
  if (v.password.length < 8) errors.password = 'A senha precisa de pelo menos 8 caracteres.';
  return errors;
}

/** Etapa 1: nome, usuário, e-mail e senha. A conta já sai logada e o código vai por e-mail. */
@Component({
  selector: 'ww-signup-account-step',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './signup-form.css',
  template: `
    <h1 #heading tabindex="-1">Criar conta</h1>
    <p class="lead">Em três passos: sua conta, do que você gosta e a confirmação do e-mail.</p>

    <form (submit)="submit($event)" novalidate>
      @for (f of fields; track f.name) {
        <label>
          <span>{{ f.label }}</span>
          @if (f.help) {
            <span class="help" [id]="'help-' + f.name">{{ f.help }}</span>
          }
          <input
            [name]="f.name"
            [type]="f.type"
            [attr.autocomplete]="f.autocomplete"
            [attr.autocapitalize]="f.name === 'displayName' ? 'words' : 'none'"
            spellcheck="false"
            required
            [attr.aria-invalid]="!!errors()[f.name]"
            [attr.aria-describedby]="describedBy(f.name, !!f.help)"
            [value]="values()[f.name]"
            (input)="set(f.name, $any($event.target).value)"
          />
          @if (errors()[f.name]; as error) {
            <span class="error" [id]="'erro-' + f.name">{{ error }}</span>
          }
        </label>
      }
      @if (alert(); as alert) {
        <p class="error alert" role="alert">{{ alert }}</p>
      }
      <button wwButton variant="primary" size="lg" type="submit" [disabled]="busy()">
        {{ busy() ? 'Criando…' : 'Continuar' }}
      </button>
    </form>
  `,
})
export class SignupAccountStep {
  private readonly auth = inject(AuthStore);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  protected readonly fields: {
    name: Field;
    label: string;
    type: string;
    autocomplete: string;
    help?: string;
  }[] = [
    { name: 'displayName', label: 'Como quer ser chamado', type: 'text', autocomplete: 'name' },
    {
      name: 'username',
      label: 'Usuário',
      type: 'text',
      autocomplete: 'username',
      help: 'É assim que seus amigos te acham. Letras minúsculas, números, _ ou ponto.',
    },
    {
      name: 'email',
      label: 'E-mail',
      type: 'email',
      autocomplete: 'email',
      help: 'Vamos mandar um código para confirmar.',
    },
    {
      name: 'password',
      label: 'Senha',
      type: 'password',
      autocomplete: 'new-password',
      help: 'Pelo menos 8 caracteres.',
    },
  ];

  protected readonly values = signal<Record<Field, string>>({
    displayName: '',
    username: '',
    email: '',
    password: '',
  });
  protected readonly errors = signal<Partial<Record<Field, string>>>({});
  protected readonly alert = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
  }

  protected set(field: Field, value: string): void {
    this.values.update((v) => ({ ...v, [field]: value }));
    if (this.errors()[field]) this.errors.update(({ [field]: _, ...rest }) => rest);
  }

  protected describedBy(field: Field, help: boolean): string | null {
    const ids = [
      help ? `help-${field}` : null,
      this.errors()[field] ? `erro-${field}` : null,
    ].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.busy()) return;
    const v = this.values();
    const errors = accountErrors(v);
    this.errors.set(errors);
    this.alert.set(null);
    if (Object.keys(errors).length) {
      this.alert.set('Confira os campos destacados.');
      return;
    }
    this.busy.set(true);
    this.auth
      .register({
        displayName: v.displayName.trim(),
        username: v.username.trim().toLowerCase(),
        email: v.email.trim(),
        password: v.password,
      })
      .subscribe({
        // a página troca de etapa sozinha (o AuthStore já tem a conta)
        error: (e: HttpErrorResponse) => {
          this.busy.set(false);
          this.alert.set(
            problemDetail(e) ?? 'Não deu pra criar a conta agora. Tente de novo em instantes.',
          );
        },
      });
  }
}
