import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';
import { guestOnly, requireLogin } from './core/auth/auth-guard';
import { RouteMeta } from './layout/route-meta';
import { profileResolver } from './features/profile/profile-resolver';

const placeholder = () =>
  import('./features/placeholder/placeholder-page').then((m) => m.PlaceholderPage);

const feed = (theme: RouteMeta['theme'] = 'light'): RouteMeta => ({ shell: 'feed', theme });
const cinema: RouteMeta = { shell: 'cinema', theme: 'dark' };

/** Telas que exigem login (todas, menos /entrar). */
const app: Routes = [
  // --- Telas principais ---
  {
    path: '',
    title: 'wannawatch?',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
    data: cinema,
  },
  {
    path: 'swipe',
    title: 'Swipe · wannawatch?',
    loadComponent: () => import('./features/swipe/swipe-page').then((m) => m.SwipePage),
    data: feed(),
  },
  {
    path: 'reels',
    title: 'Reels · wannawatch?',
    loadComponent: () => import('./features/reels/reels-page').then((m) => m.ReelsPage),
    data: feed('dark'),
  },
  {
    path: 'match',
    title: 'Match · wannawatch?',
    loadComponent: () => import('./features/match/match-lobby-page').then((m) => m.MatchLobbyPage),
    data: feed(),
  },
  {
    path: 'match/:code',
    title: 'Sala de Match · wannawatch?',
    loadComponent: () => import('./features/match/match-room-page').then((m) => m.MatchRoomPage),
    data: feed(),
  },
  {
    path: 'filme/:id',
    title: 'Filme · wannawatch?',
    loadComponent: () => import('./features/details/details-page').then((m) => m.DetailsPage),
    data: { ...cinema, mediaType: 'MOVIE' },
  },
  {
    path: 'serie/:id',
    title: 'Série · wannawatch?',
    loadComponent: () => import('./features/details/details-page').then((m) => m.DetailsPage),
    data: { ...cinema, mediaType: 'SERIES' },
  },
  {
    path: 'busca',
    title: 'Busca · wannawatch?',
    loadComponent: () => import('./features/search/search-page').then((m) => m.SearchPage),
    data: cinema,
  },

  // --- Fora do escopo desta etapa ---
  {
    path: 'series',
    title: 'Séries · wannawatch?',
    loadComponent: () => import('./features/search/search-page').then((m) => m.SearchPage),
    data: { ...feed(), heading: 'Séries', message: 'Uma vitrine só de séries vem depois.', icon: 'tv' },
  },
  {
    path: 'filmes',
    title: 'Filmes · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Filmes', message: 'Uma vitrine só de filmes vem depois.', icon: 'film' },
  },
  {
    path: 'minha-lista',
    title: 'Minha lista · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Minha lista', message: 'O que você salvar aparece aqui. Em breve.', icon: 'list' },
  },
  {
    path: 'perfil',
    title: 'Perfil · wannawatch?',
    loadComponent: () => import('./features/profile/profile-page').then((m) => m.ProfilePage),
    resolve: { savedProfile: profileResolver },
    data: feed(),
  },
  {
    path: 'sobre',
    title: 'Sobre e créditos · wannawatch?',
    loadComponent: () => import('./features/about/about-page').then((m) => m.AboutPage),
    data: feed(),
  },

  ...(isDevMode()
    ? [
        {
          path: 'dev/componentes',
          title: 'Componentes · wannawatch?',
          loadComponent: () => import('./features/dev/ui-catalog').then((m) => m.UiCatalog),
          data: feed(),
        },
      ]
    : []),
];

export const routes: Routes = [
  {
    path: 'entrar',
    title: 'Entrar · wannawatch?',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
    canActivate: [guestOnly],
    data: { shell: 'bare', theme: 'light' } satisfies RouteMeta,
  },
  { path: '', canActivateChild: [requireLogin], children: app },
  {
    path: '**',
    title: 'Não encontrado · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Ops, essa página não existe', message: 'Talvez o link tenha mudado.', mascot: true },
  },
];
