import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  isDevMode,
  untracked,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthStore, SignupStep } from '../../core/auth/auth-store';
import { AuthArt, AuthFrame } from '../../shared/ui/auth-frame';
import { Logo } from '../../shared/ui/logo';
import { safeReturn } from '../login/login-page';
import { SignupAccountStep } from './signup-account-step';
import { SignupEmailStep } from './signup-email-step';
import { SignupTitlesStep } from './signup-titles-step';

const STEPS: { key: Exclude<SignupStep, 'done'>; label: string; art: AuthArt }[] = [
  { key: 'account', label: 'Sua conta', art: 'cadastro' },
  { key: 'titles', label: 'Do que você gosta', art: 'favoritos' },
  { key: 'email', label: 'Confirme o e-mail', art: 'email' },
];

/**
 * Cadastro em 3 etapas: conta → pelo menos 5 títulos (e um favorito, opcional) → confirmar o
 * e-mail. A etapa vem do AuthStore (que vem do back), então recarregar a página ou entrar de novo
 * continua de onde parou. Terminou: vai para ?volta= ou para o início.
 */
@Component({
  selector: 'ww-signup-page',
  imports: [AuthFrame, Logo, RouterLink, SignupAccountStep, SignupTitlesStep, SignupEmailStep],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signup-page.html',
  styleUrl: './signup-page.css',
})
export class SignupPage {
  /** ?volta=/swipe — para onde ir no fim */
  readonly volta = input<string>();

  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  protected readonly dev = isDevMode();

  protected readonly steps = STEPS;
  protected readonly step = this.auth.signupStep;
  protected readonly index = computed(() => STEPS.findIndex((s) => s.key === this.step()));
  /** Cada etapa tem o seu Bu na moldura. */
  protected readonly art = computed(() => STEPS[Math.max(this.index(), 0)].art);

  constructor() {
    effect(() => {
      if (this.step() === 'done')
        untracked(() => this.router.navigateByUrl(safeReturn(this.volta())));
    });
  }

  /** Sair no meio do cadastro: dá para continuar depois entrando de novo. */
  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/');
  }
}
