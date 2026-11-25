import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth-store';

/** Sem login, qualquer tela manda para /entrar (e volta para onde ia depois de entrar). */
export const requireLogin: CanActivateFn = (_route, state) => {
  if (inject(AuthStore).loggedIn()) return true;
  const back = state.url;
  return inject(Router).createUrlTree(['/entrar'], { queryParams: back && back !== '/' ? { volta: back } : {} });
};

/** Quem já entrou não precisa ver a tela de login. */
export const guestOnly: CanActivateFn = () => {
  if (!inject(AuthStore).loggedIn()) return true;
  return inject(Router).createUrlTree(['/']);
};
