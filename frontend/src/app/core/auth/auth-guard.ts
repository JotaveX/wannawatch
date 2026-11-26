import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthStore } from './auth-store';

/** /entrar?volta=/swipe — "/" não precisa de volta. */
export function loginUrl(router: Router, back: string): UrlTree {
  return router.createUrlTree(['/entrar'], {
    queryParams: back && back !== '/' ? { volta: back } : {},
  });
}

/** Swipe, Reels, Match e perfil: sem login, manda para /entrar (e volta para onde ia depois). */
export const requireLogin: CanActivateFn = (_route, state) => {
  if (inject(AuthStore).loggedIn()) return true;
  return loginUrl(inject(Router), state.url);
};

/**
 * Quem entrou mas não terminou o cadastro (títulos ou e-mail) volta para /cadastro em qualquer
 * tela. Sem login passa: o catálogo é aberto.
 */
export const requireSignupDone: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (!auth.loggedIn() || auth.ready()) return true;
  const back = state.url;
  return inject(Router).createUrlTree(['/cadastro'], {
    queryParams: back && back !== '/' ? { volta: back } : {},
  });
};

/** Telas de administrador: quem não é admin volta para o início. */
export const requireAdmin: CanActivateFn = () => {
  if (inject(AuthStore).isAdmin()) return true;
  return inject(Router).createUrlTree(['/']);
};

/** Quem já entrou não precisa ver a tela de login. */
export const guestOnly: CanActivateFn = () => {
  if (!inject(AuthStore).loggedIn()) return true;
  return inject(Router).createUrlTree(['/']);
};

/** /cadastro serve para quem não tem conta ou ainda não terminou; quem terminou vai para o início. */
export const signupPending: CanActivateFn = () => {
  if (!inject(AuthStore).ready()) return true;
  return inject(Router).createUrlTree(['/']);
};
