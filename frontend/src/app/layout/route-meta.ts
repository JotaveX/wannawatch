/**
 * Metadados de layout de cada rota (em `data`):
 *   shell "feed"   → barra lateral no desktop (Swipe, Reels, telas secundárias)
 *   shell "cinema" → a própria página traz o menu do topo (Home, detalhes, busca)
 *   shell "bare"   → sem navegação nenhuma (login)
 * A barra de abas aparece no celular nos dois primeiros casos.
 */
export type Shell = 'feed' | 'cinema' | 'bare';
export type Theme = 'light' | 'dark';

export interface RouteMeta {
  shell: Shell;
  theme: Theme;
}

export const DEFAULT_META: RouteMeta = { shell: 'feed', theme: 'light' };
