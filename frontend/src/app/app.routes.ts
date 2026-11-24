import { isDevMode } from '@angular/core';
import { Routes } from '@angular/router';
import { RouteMeta } from './layout/route-meta';

const placeholder = () =>
  import('./features/placeholder/placeholder-page').then((m) => m.PlaceholderPage);

const feed = (theme: RouteMeta['theme'] = 'light'): RouteMeta => ({ shell: 'feed', theme });
const cinema: RouteMeta = { shell: 'cinema', theme: 'dark' };

export const routes: Routes = [
  // --- Telas principais (por enquanto provisórias; cada uma chega na sua fase) ---
  {
    path: '',
    title: 'wannawatch?',
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
    data: cinema,
  },
  {
    path: 'swipe',
    title: 'Swipe · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Swipe', message: 'Passa, talvez ou quero ver: chega na fase 7.', icon: 'swipe' },
  },
  {
    path: 'reels',
    title: 'Reels · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed('dark'), heading: 'Reels', message: 'O feed de sinopses chega na fase 8.', icon: 'reels' },
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
    loadComponent: placeholder,
    data: { ...cinema, heading: 'Busca', message: 'A busca chega na fase 6.', icon: 'search' },
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
    data: { ...feed(), heading: 'Minha lista', message: 'Quando tiver login, o que você salvar aparece aqui.', icon: 'list' },
  },
  {
    path: 'grupos',
    title: 'Grupos · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Grupos', message: 'Criar grupo, chamar a galera e ver os matches: em breve.', icon: 'groups' },
  },
  {
    path: 'perfil',
    title: 'Perfil · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Perfil', message: 'Login e perfil chegam numa próxima etapa.', icon: 'profile' },
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

  {
    path: '**',
    title: 'Não encontrado · wannawatch?',
    loadComponent: placeholder,
    data: { ...feed(), heading: 'Ops, essa página não existe', message: 'Talvez o link tenha mudado.', icon: 'info' },
  },
];
