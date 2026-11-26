import { TitleCard } from './api/models';
import { countryName, duration, languageName, longDate, metaLine, money, seasons } from './format';

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
      metaLine({
        ...base,
        mediaType: 'MOVIE',
        runtimeMinutes: 130,
        genres: [{ id: 1, name: 'Drama', slug: 'drama' }],
      }),
    ).toBe('Filme · Drama · 2h10');
    expect(metaLine({ ...base, mediaType: 'SERIES', seasonsCount: 2 })).toBe(
      'Série · 2 temporadas',
    );
  });
});

describe('format (detalhes)', () => {
  it('traduz país e idioma', () => {
    expect(countryName('GB', 'United Kingdom')).toBe('Reino Unido');
    expect(countryName('US')).toBe('Estados Unidos');
    expect(languageName('en')).toBe('Inglês');
    expect(languageName(null)).toBeNull();
  });

  it('formata dinheiro em dólar compacto e esconde zero', () => {
    expect(money(165_000_000)).toMatch(/^US\$\s?165\smi$/);
    expect(money(746_606_706)).toMatch(/^US\$\s?746,6\smi$/);
    expect(money(0)).toBeNull();
    expect(money(null)).toBeNull();
  });

  it('escreve a data por extenso sem erro de fuso', () => {
    expect(longDate('2014-11-04')).toBe('4 de novembro de 2014');
    expect(longDate(null)).toBeNull();
  });
});
