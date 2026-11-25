import { participantNames, roomFilters } from './match-format';

const u = (id: number, displayName: string) => ({ user: { id, username: displayName.toLowerCase(), displayName }, votes: 0, likes: 0, joinedAt: '' });

describe('roomFilters', () => {
  it('descreve tipo e gênero', () => {
    expect(roomFilters({ mediaType: null, genre: null })).toBe('Filmes e séries');
    expect(roomFilters({ mediaType: 'SERIES', genre: null })).toBe('Séries');
    expect(roomFilters({ mediaType: 'MOVIE', genre: { id: 1, name: 'Drama', slug: 'drama' } })).toBe('Filmes · Drama');
  });
});

describe('participantNames', () => {
  it('põe "Você" primeiro e junta com "e"', () => {
    expect(participantNames({ participants: [u(2, 'Bia'), u(1, 'Jotave'), u(3, 'Caio')] }, 1)).toBe('Você, Bia e Caio');
    expect(participantNames({ participants: [u(1, 'Jotave')] }, 1)).toBe('Você');
  });
});
