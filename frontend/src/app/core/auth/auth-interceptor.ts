import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import { AuthStore } from './auth-store';

/**
 * Põe o token nas chamadas à nossa API. Se a API responder 401 (token vencido ou apagado),
 * esquece a sessão e manda para o login, guardando onde a pessoa estava.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(API_BASE_URL);
  if (!req.url.startsWith(base)) return next(req);

  const auth = inject(AuthStore);
  const router = inject(Router);
  const token = auth.token();
  const authed = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authed).pipe(
    catchError((error: unknown) => {
      const isLogin = req.url.endsWith('/auth/login');
      if (error instanceof HttpErrorResponse && error.status === 401 && !isLogin && auth.loggedIn()) {
        auth.clear();
        const back = router.url;
        router.navigate(['/entrar'], { queryParams: back && back !== '/' ? { volta: back } : {} });
      }
      return throwError(() => error);
    }),
  );
};
