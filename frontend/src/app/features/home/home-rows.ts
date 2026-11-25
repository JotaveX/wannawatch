import { FriendsLiked, HomeResponse, RowKind, TitleCard } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { likedByLabel } from '../../core/social/likes-store';
import { RowItem } from '../../shared/ui/wide-card';

export type RowView =
  | { key: string; kind: 'TOP10'; title: string; items: TitleCard[] }
  | { key: string; kind: Exclude<RowKind, 'TOP10'> | 'FRIENDS'; title: string; items: RowItem[] };

/**
 * Monta as fileiras da Home: as da API, na ordem dela, mais "Seus amigos curtiram"
 * (curtidas reais dos outros usuários) logo depois do top 10.
 */
export function buildHomeRows(home: HomeResponse, friendsLiked: readonly FriendsLiked[]): RowView[] {
  const api: RowView[] = home.rows.map((row) =>
    row.kind === 'TOP10'
      ? { key: row.key, kind: 'TOP10', title: row.title, items: row.items }
      : { key: row.key, kind: row.kind, title: row.title, items: row.items.map((card) => ({ card, sub: metaLine(card) })) },
  );

  const friendsRow: RowView = {
    key: 'friends',
    kind: 'FRIENDS',
    title: 'Seus amigos curtiram',
    items: friendsLiked.map(({ title, friends }) => ({ card: title, sub: likedByLabel(friends) ?? metaLine(title) })),
  };

  const top = api.filter((r) => r.kind === 'TOP10');
  const rest = api.filter((r) => r.kind !== 'TOP10');
  return [...top, friendsRow, ...rest].filter((r) => r.items.length > 0);
}
