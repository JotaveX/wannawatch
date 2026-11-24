import { TestBed } from '@angular/core/testing';
import { SocialMock } from '../../mocks/social.mock';
import { homeResponse } from '../../../testing/fixtures';
import { buildHomeRows, heroReason } from './home-rows';

describe('buildHomeRows', () => {
  let social: SocialMock;
  beforeEach(() => (social = TestBed.inject(SocialMock)));

  it('põe continue assistindo, top 10 e match antes das fileiras da API', () => {
    const rows = buildHomeRows(homeResponse(), social);
    expect(rows.map((r) => r.kind)).toEqual(['CONTINUE', 'TOP10', 'MATCH', 'SIMILAR', 'BINGE']);
  });

  it('não usa o destaque nem repete título nas fileiras de mentira', () => {
    const rows = buildHomeRows(homeResponse(), social);
    const mockIds = rows
      .filter((r) => r.kind === 'CONTINUE' || r.kind === 'MATCH')
      .flatMap((r) => (r.kind === 'TOP10' ? [] : r.items.map((i) => i.card.id)));
    expect(mockIds).not.toContain(1);
    expect(new Set(mockIds).size).toBe(mockIds.length);
  });

  it('ordena o match pelo % e troca o texto no modo solo', () => {
    const group = buildHomeRows(homeResponse(), social).find((r) => r.kind === 'MATCH')!;
    expect(group.title).toBe('Deu match na Sexta do cinema');
    const pcts = group.kind === 'TOP10' ? [] : group.items.map((i) => parseInt(i.badge!, 10));
    expect(pcts).toEqual([...pcts].sort((a, b) => b - a));
    expect(group.kind !== 'TOP10' && group.items[0].sub).toMatch(/^\d de 4 querem ver$/);

    social.mode.set('solo');
    const solo = buildHomeRows(homeResponse(), social).find((r) => r.kind === 'MATCH')!;
    expect(solo.title).toBe('Na sua lista de talvez');
  });

  it('mostra tipo, gênero e duração embaixo dos cards da API', () => {
    const similar = buildHomeRows(homeResponse(), social).find((r) => r.kind === 'SIMILAR')!;
    expect(similar.kind !== 'TOP10' && similar.items[0].sub).toBe('Filme · Ficção científica · 2h10');
  });

  it('some com fileiras vazias', () => {
    const rows = buildHomeRows(homeResponse({ hero: null, rows: [] }), social);
    expect(rows).toEqual([]);
  });

  it('explica o destaque pelo grupo ou pelo gênero', () => {
    const hero = homeResponse().hero!;
    expect(heroReason(hero, social)).toMatch(/^\d de 4 amigos da Sexta do cinema querem ver$/);
    social.mode.set('solo');
    expect(heroReason(hero, social)).toBe('Porque você curte ficção científica');
  });
});
