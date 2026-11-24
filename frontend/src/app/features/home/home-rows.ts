import { HomeResponse, RowKind, TitleCard } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { SocialMock } from '../../mocks/social.mock';

/** Item já pronto para o card: o título + o que aparece embaixo + selo/progresso opcionais. */
export interface RowItem {
  card: TitleCard;
  sub: string;
  badge?: string;
  /** 0–100, barra do "continue assistindo" */
  progress?: number;
}

export type RowView =
  | { key: string; kind: 'TOP10'; title: string; items: TitleCard[] }
  | { key: string; kind: Exclude<RowKind, 'TOP10'> | 'CONTINUE' | 'MATCH'; title: string; items: RowItem[] };

const CONTINUE_SIZE = 7;
const MATCH_SIZE = 6;

/**
 * Monta as fileiras da Home: as da API, na ordem dela, mais duas de mentira
 * ("Continue assistindo" e "Deu match"/"Na sua lista de talvez") feitas com títulos reais.
 *
 * Ordem final (igual ao protótipo): continue · top 10 · match · o resto da API.
 */
export function buildHomeRows(home: HomeResponse, social: SocialMock): RowView[] {
  const api: RowView[] = home.rows.map((row) =>
    row.kind === 'TOP10'
      ? { key: row.key, kind: 'TOP10', title: row.title, items: row.items }
      : { key: row.key, kind: row.kind, title: row.title, items: row.items.map((card) => ({ card, sub: metaLine(card) })) },
  );

  // Títulos de onde tirar os mocks: tudo que veio, sem repetir, sem o destaque.
  const pool = unique(home.rows.flatMap((r) => r.items)).filter((c) => c.id !== home.hero?.id);
  const continuePicks = pool.filter((_, i) => i % 3 === 0).slice(0, CONTINUE_SIZE);
  const matchPicks = pool.filter((_, i) => i % 3 === 1).slice(0, MATCH_SIZE);

  const continueRow: RowView = {
    key: 'mock-continue',
    kind: 'CONTINUE',
    title: 'Continue assistindo',
    items: social.continueWatching(continuePicks).map((c) => ({ card: c.title, sub: c.label, progress: c.progress })),
  };

  const members = social.group.members;
  const matchRow: RowView = {
    key: 'mock-match',
    kind: 'MATCH',
    title: social.isGroup() ? `Deu match na ${social.group.name}` : 'Na sua lista de talvez',
    items: [...matchPicks]
      .sort((a, b) => social.matchPercent(b.id) - social.matchPercent(a.id))
      .map((card) => ({
        card,
        badge: `${social.matchPercent(card.id)}% match`,
        sub: social.isGroup() ? `${social.friendsWhoWant(card.id)} de ${members} querem ver` : metaLine(card),
      })),
  };

  const top = api.filter((r) => r.kind === 'TOP10');
  const rest = api.filter((r) => r.kind !== 'TOP10');
  return [continueRow, ...top, matchRow, ...rest].filter((r) => r.items.length > 0);
}

/** Frase embaixo da sinopse do destaque. */
export function heroReason(hero: TitleCard, social: SocialMock): string {
  if (social.isGroup()) {
    return `${social.friendsWhoWant(hero.id)} de ${social.group.members} amigos da ${social.group.name} querem ver`;
  }
  const genre = hero.genres[0]?.name;
  return genre ? `Porque você curte ${genre.toLowerCase()}` : 'Escolhido pra você';
}

function unique(cards: TitleCard[]): TitleCard[] {
  const seen = new Set<number>();
  return cards.filter((c) => !seen.has(c.id) && seen.add(c.id));
}
