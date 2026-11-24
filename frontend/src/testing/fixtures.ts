import { HomeResponse, TitleCard } from '../app/core/api/models';

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
