import { FriendsLiked } from '../../core/api/models';
import { card, homeResponse } from '../../../testing/fixtures';
import { buildHomeRows } from './home-rows';

const bia = { id: 2, username: 'bia', displayName: 'Bia' };
const caio = { id: 3, username: 'caio', displayName: 'Caio' };
const friends: FriendsLiked[] = [
  { title: card(90, { title: 'Curtido' }), likedByMe: false, friends: [bia, caio] },
  { title: card(91), likedByMe: true, friends: [] },
];

describe('buildHomeRows', () => {
  it('põe top 10 e "Seus amigos curtiram" antes das fileiras da API', () => {
    const rows = buildHomeRows(homeResponse(), friends);
    expect(rows.map((r) => r.kind)).toEqual(['TOP10', 'FRIENDS', 'SIMILAR', 'BINGE']);
    expect(rows[1].title).toBe('Seus amigos curtiram');
  });

  it('diz quem curtiu embaixo de cada card dos amigos', () => {
    const row = buildHomeRows(homeResponse(), friends).find((r) => r.kind === 'FRIENDS')!;
    expect(row.kind !== 'TOP10' && row.items.map((i) => i.sub)).toEqual([
      'Bia e Caio curtiram',
      'Filme · Ficção científica · 2h10',
    ]);
  });

  it('sem curtidas dos amigos, a fileira some', () => {
    const rows = buildHomeRows(homeResponse(), []);
    expect(rows.map((r) => r.kind)).not.toContain('FRIENDS');
  });

  it('mostra tipo, gênero e duração embaixo dos cards da API', () => {
    const similar = buildHomeRows(homeResponse(), []).find((r) => r.kind === 'SIMILAR')!;
    expect(similar.kind !== 'TOP10' && similar.items[0].sub).toBe('Filme · Ficção científica · 2h10');
  });

  it('"Pra você" vem logo depois do top 10, antes dos amigos, com o motivo embaixo de cada card', () => {
    const forYou = {
      key: 'for-you',
      kind: 'FOR_YOU' as const,
      title: 'Pra você',
      items: [card(70, { reason: { kind: 'BECAUSE_LIKED', text: 'Porque você curtiu Dark' } }), card(71)],
    };
    const base = homeResponse();
    const rows = buildHomeRows({ ...base, rows: [base.rows[0], forYou, ...base.rows.slice(1)] }, friends);
    expect(rows.map((r) => r.kind)).toEqual(['TOP10', 'FOR_YOU', 'FRIENDS', 'SIMILAR', 'BINGE']);
    const row = rows[1];
    expect(row.kind !== 'TOP10' && row.items.map((i) => i.sub)).toEqual([
      'Porque você curtiu Dark',
      'Filme · Ficção científica · 2h10',
    ]);
  });

  it('some com fileiras vazias', () => {
    expect(buildHomeRows(homeResponse({ hero: null, rows: [] }), [])).toEqual([]);
  });
});
