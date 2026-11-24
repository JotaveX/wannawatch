import { TitleCard } from './api/models';
import { duration, metaLine, seasons } from './format';

describe('format', () => {
  it('formata duração', () => {
    expect(duration(130)).toBe('2h10');
    expect(duration(125)).toBe('2h05');
    expect(duration(120)).toBe('2h');
    expect(duration(45)).toBe('45 min');
    expect(duration(null)).toBeNull();
    expect(duration(0)).toBeNull();
  });

  it('formata temporadas no singular e no plural', () => {
    expect(seasons(1)).toBe('1 temporada');
    expect(seasons(4)).toBe('4 temporadas');
    expect(seasons(null)).toBeNull();
  });

  it('monta a linha de informações sem partes vazias', () => {
    const base = { genres: [], poster: null, backdrop: null } as unknown as TitleCard;
    expect(
      metaLine({ ...base, mediaType: 'MOVIE', runtimeMinutes: 130, genres: [{ id: 1, name: 'Drama', slug: 'drama' }] }),
    ).toBe('Filme · Drama · 2h10');
    expect(metaLine({ ...base, mediaType: 'SERIES', seasonsCount: 2 })).toBe('Série · 2 temporadas');
  });
});
