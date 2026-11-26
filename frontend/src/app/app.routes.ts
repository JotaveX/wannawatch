import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';
import {
  guestOnly,
  requireAdmin,
  requireLogin,
  requireSignupDone,
  signupPending,
} from './core/auth/auth-guard';
import { RouteMeta } from './layout/route-meta';
import { profileResolver } from './features/profile/profile-resolver';

const placeholder = () =>
  import('./features/placeholder/placeholder-page').then((m) => m.PlaceholderPage);

const feed = (theme: RouteMeta['theme'] = 'light'): RouteMeta => ({ shell: 'feed', theme });
const cinema: RouteMeta = { shell: 'cinema', theme: 'dark' };

/** Abertas para quem não tem conta: o catálogo (Início, detalhes, busca) e o Sobre. */
const open: Routes = [
  {
    path: '',
    title: 'wannawatch?',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
    data: cinema,
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
  {
    path: 'series',
    title: 'Séries · wannawatch?',
    loadComponent: () => import('./features/search/search-page').then((m) => m.SearchPage),
    // a busca é sempre escura: com o tema claro o fundo atrás dela piscava branco na troca
    data: {
      ...feed('dark'),
      heading: 'Séries',
      message: 'Uma vitrine só de séries vem depois.',
      icon: 'tv',
    },
  },
  {
    path: 'filmes',
    title: 'Filmes · wannawatch?',
    loadComponent: () => import('./features/search/search-page').then((m) => m.SearchPage),
    data: {
      ...feed('dark'),
      heading: 'Filmes',
      message: 'Uma vitrine só de filmes vem depois.',
      icon: 'film',
    },
  },
  {
    path: 'sobre',
    title: 'Sobre e créditos · wannawatch?',
    loadComponent: () => import('./features/about/about-page').then((m) => m.AboutPage),
    data: feed(),
  },
];

/** Precisam de conta: sem login, vão para /entrar e voltam depois. */
const members: Routes = [
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
    path: 'perfil',
    title: 'Perfil · wannawatch?',
    loadComponent: () => import('./features/profile/profile-page').then((m) => m.ProfilePage),
    resolve: { savedProfile: profileResolver },
    data: feed(),
  },
  {
    path: 'ajustes',
    title: 'Ajustes · wannawatch?',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
    resolve: { savedProfile: profileResolver },
    data: feed(),
  },
  {
    path: 'admin/videos',
    title: 'Vídeos do Reels · wannawatch?',
    loadComponent: () =>
      import('./features/admin-videos/admin-videos-page').then((m) => m.AdminVideosPage),
    canActivate: [requireAdmin],
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
  {
    path: 'cadastro',
    title: 'Criar conta · wannawatch?',
    loadComponent: () => import('./features/signup/signup-page').then((m) => m.SignupPage),
    canActivate: [signupPending],
    data: { shell: 'bare', theme: 'light' } satisfies RouteMeta,
  },
  {
    path: '',
    // quem entrou mas não terminou o cadastro volta para /cadastro em qualquer tela
    canActivateChild: [requireSignupDone],
    children: [...open, { path: '', canActivateChild: [requireLogin], children: members }],
  },
  {
    path: '**',
    title: 'Não encontrado · wannawatch?',
    loadComponent: placeholder,
    data: {
      ...feed(),
      heading: 'Ops, essa página não existe',
      message: 'Talvez o link tenha mudado.',
      mascot: true,
    },
  },
];
