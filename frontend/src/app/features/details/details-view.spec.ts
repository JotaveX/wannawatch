import { detail } from '../../../testing/fixtures';
import { crewGroups, infoItems, initials, providerGroups } from './details-view';

describe('details-view', () => {
  it('monta a ficha técnica traduzida e sem campos vazios', () => {
    const info = Object.fromEntries(infoItems(detail()).map((i) => [i.label, i.value]));
    expect(info['Título original']).toBe('Interstellar');
    expect(info['Lançamento']).toBe('4 de novembro de 2014');
    expect(info['Idioma original']).toBe('Inglês');
    expect(info['Países']).toBe('Reino Unido');
    expect(info['Orçamento']).toMatch(/165\smi/);
    expect(info['Nota na TMDB']).toBe('7,5 (42.005 votos)');
    expect(info['Emissoras']).toBeUndefined();
    expect(info['Temporadas']).toBeUndefined();
  });

  it('esconde título original igual, orçamento zero e mostra temporadas de série', () => {
    const d = detail({ mediaType: 'SERIES', originalTitle: 'Interestelar', budget: 0, revenue: null, seasonsCount: 2, episodesCount: 16 });
    const labels = infoItems(d).map((i) => i.label);
    expect(labels).not.toContain('Título original');
    expect(labels).not.toContain('Orçamento');
    expect(labels).toContain('Estreia');
    expect(labels).toContain('Temporadas');
    expect(labels).toContain('Episódios');
  });

  it('junta a equipe por função sem repetir nomes', () => {
    expect(crewGroups(detail().crew)).toEqual([
      { job: 'Direção', names: ['Christopher Nolan'] },
      { job: 'Roteiro', names: ['Jonathan Nolan', 'Christopher Nolan'] },
    ]);
  });

  it('agrupa onde assistir e pula grupos vazios', () => {
    expect(providerGroups(detail().watchProviders).map((g) => g.label)).toEqual(['Assinatura', 'Aluguel']);
  });

  it('gera iniciais para quem não tem foto', () => {
    expect(initials({ name: 'Matthew McConaughey' })).toBe('MM');
    expect(initials({ name: 'Zendaya' })).toBe('Z');
  });
});
