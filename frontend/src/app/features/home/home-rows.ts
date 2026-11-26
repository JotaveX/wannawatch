import { FriendsLiked, HomeResponse, RowKind, TitleCard } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { likedByLabel } from '../../core/social/likes-store';
import { RowItem } from '../../shared/ui/wide-card';

export type RowView =
  | { key: string; kind: 'TOP10'; title: string; items: TitleCard[] }
  | { key: string; kind: Exclude<RowKind, 'TOP10'> | 'FRIENDS'; title: string; items: RowItem[] };

/**
 * Monta as fileiras da Home: as da API, na ordem dela, mais "Seus amigos curtiram"
 * (curtidas reais dos outros usuários) logo depois do top 10 e do "Pra você".
 * A linha de baixo de cada card é o motivo da recomendação, quando houver, ou os dados do título.
 */
export function buildHomeRows(home: HomeResponse, friendsLiked: readonly FriendsLiked[]): RowView[] {
  const api: RowView[] = home.rows.map((row) =>
    row.kind === 'TOP10'
      ? { key: row.key, kind: 'TOP10', title: row.title, items: row.items }
      : { key: row.key, kind: row.kind, title: row.title, items: row.items.map((card) => ({ card, sub: card.reason?.text ?? metaLine(card) })) },
  );

  const friendsRow: RowView = {
    key: 'friends',
    kind: 'FRIENDS',
    title: 'Seus amigos curtiram',
    items: friendsLiked.map(({ title, friends }) => ({ card: title, sub: likedByLabel(friends) ?? metaLine(title) })),
  };

  const first = (r: RowView) => r.kind === 'TOP10' || r.kind === 'FOR_YOU';
  const top = api.filter(first);
  const rest = api.filter((r) => !first(r));
  return [...top, friendsRow, ...rest].filter((r) => r.items.length > 0);
}
