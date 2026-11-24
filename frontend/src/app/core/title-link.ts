import { TitleCard } from './api/models';

/** Rota da página de detalhes: /filme/42 ou /serie/42 */
export function titleLink(card: Pick<TitleCard, 'id' | 'mediaType'>): string[] {
  return [card.mediaType === 'MOVIE' ? '/filme' : '/serie', String(card.id)];
}
