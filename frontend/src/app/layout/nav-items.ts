import { IconName } from '../shared/ui/icon';

export interface NavItem {
  label: string;
  path: string;
  icon: IconName;
  /** só "/" precisa de match exato */
  exact?: boolean;
}

/** Barra de abas do celular. */
export const TAB_ITEMS: NavItem[] = [
  { label: 'Início', path: '/', icon: 'home', exact: true },
  { label: 'Swipe', path: '/swipe', icon: 'swipe' },
  { label: 'Reels', path: '/reels', icon: 'reels' },
  { label: 'Match', path: '/match', icon: 'groups' },
  { label: 'Perfil', path: '/perfil', icon: 'profile' },
];

/** Sem login, no lugar do Perfil. */
export const GUEST_TAB: NavItem = { label: 'Entrar', path: '/entrar', icon: 'profile' };

/** Barra lateral do desktop (estilo TikTok web). */
export const SIDE_ITEMS: NavItem[] = [
  { label: 'Início', path: '/', icon: 'home', exact: true },
  { label: 'Swipe', path: '/swipe', icon: 'swipe' },
  { label: 'Reels', path: '/reels', icon: 'reels' },
  { label: 'Séries', path: '/series', icon: 'tv' },
  { label: 'Filmes', path: '/filmes', icon: 'film' },
  { label: 'Minha lista', path: '/minha-lista', icon: 'list' },
  { label: 'Match', path: '/match', icon: 'groups' },
  { label: 'Ajustes', path: '/ajustes', icon: 'settings' },
];
