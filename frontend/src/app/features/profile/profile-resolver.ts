import { inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Profile } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';

/**
 * Traz o perfil antes de abrir a página. O router só começa a View Transition depois dos
 * resolvers: assim a foto da página nova já sai no tamanho final, em vez de "Carregando…"
 * que cresce no meio da animação (e era espremido até caber no tamanho capturado).
 * Se falhar, devolve null e a página tenta de novo sozinha (com o botão de "Tentar de novo").
 */
export const profileResolver: ResolveFn<Profile | null> = () =>
  inject(SocialApi)
    .profile()
    .pipe(catchError(() => of(null)));
