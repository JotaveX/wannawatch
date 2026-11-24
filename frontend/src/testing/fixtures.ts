import { HomeResponse, TitleCard, TitleDetail } from '../app/core/api/models';

export function card(id: number, over: Partial<TitleCard> = {}): TitleCard {
  return {
    id,
    mediaType: 'MOVIE',
    title: `Título ${id}`,
    year: 2026,
    genres: [{ id: 1, name: 'Ficção científica', slug: 'ficcao-cientifica' }],
    overview: 'Sinopse.',
    runtimeMinutes: 130,
    seasonsCount: null,
    ageRating: '14',
    voteAverage: 7.5,
    poster: null,
    backdrop: null,
    ...over,
  };
}

const range = (from: number, n: number) => Array.from({ length: n }, (_, i) => card(from + i));

export function homeResponse(over: Partial<HomeResponse> = {}): HomeResponse {
  return {
    hero: { ...card(1, { title: 'Noite em Marte' }), tagline: 'Ninguém fica sozinho', trailerUrl: 'https://www.youtube.com/watch?v=abc' },
    rows: [
      { key: 'top10', kind: 'TOP10', title: 'Top 10 de hoje no wannawatch?', items: range(1, 10) },
      { key: 'similar', kind: 'SIMILAR', title: 'Porque você curtiu Noite em Marte', items: range(20, 15) },
      { key: 'binge', kind: 'BINGE', title: 'Séries pra maratonar', items: range(40, 15).map((c) => ({ ...c, mediaType: 'SERIES', seasonsCount: 3 })) },
    ],
    ...over,
  };
}

export function detail(over: Partial<TitleDetail> = {}): TitleDetail {
  return {
    ...card(79, { title: 'Interestelar', runtimeMinutes: 169 }),
    originalTitle: 'Interstellar',
    tagline: 'O fim da terra não será o nosso fim.',
    releaseDate: '2014-11-04',
    status: 'Lançado',
    originalLanguage: 'en',
    episodesCount: null,
    voteCount: 42005,
    budget: 165_000_000,
    revenue: 746_606_706,
    trailerUrl: 'https://www.youtube.com/watch?v=x',
    cast: [
      { personId: 1, name: 'Matthew McConaughey', character: 'Cooper', photoUrl: null },
      { personId: 2, name: 'Anne Hathaway', character: 'Brand', photoUrl: 'https://img/p.jpg' },
    ],
    crew: [
      { personId: 10, name: 'Christopher Nolan', job: 'Direção', photoUrl: null },
      { personId: 11, name: 'Jonathan Nolan', job: 'Roteiro', photoUrl: null },
      { personId: 10, name: 'Christopher Nolan', job: 'Roteiro', photoUrl: null },
    ],
    productionCompanies: [{ name: 'Syncopy', logoUrl: null }],
    productionCountries: [{ code: 'GB', name: 'United Kingdom' }],
    networks: [],
    watchProviders: {
      link: 'https://www.themoviedb.org/movie/157336/watch?locale=BR',
      streaming: [{ name: 'Amazon Prime Video', logoUrl: null }],
      free: [],
      ads: [],
      rent: [{ name: 'Apple TV Store', logoUrl: null }],
      buy: [],
    },
    similar: [card(41, { title: 'Homem de Ferro' })],
    ...over,
  };
}
