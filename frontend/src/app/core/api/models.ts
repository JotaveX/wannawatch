/**
 * Contratos da API do back-end (espelho de CatalogDtos.java).
 * O front depende só destes tipos — nunca da TMDB.
 */

export type MediaType = 'MOVIE' | 'SERIES';

export interface GenreRef {
  id: number;
  name: string;
  slug: string;
}

export interface Genre extends GenreRef {
  forMovie: boolean;
  forSeries: boolean;
  titleCount: number;
}

/** URL completa em dois tamanhos, para srcset. */
export interface ImageSet {
  small: string;
  large: string;
}

export interface TitleCard {
  id: number;
  mediaType: MediaType;
  title: string;
  year: number | null;
  genres: GenreRef[];
  overview: string | null;
  runtimeMinutes: number | null;
  seasonsCount: number | null;
  ageRating: string | null;
  voteAverage: number | null;
  poster: ImageSet | null;
  backdrop: ImageSet | null;
}

export interface CastMember {
  personId: number;
  name: string;
  character: string | null;
  photoUrl: string | null;
}

export interface CrewMember {
  personId: number;
  name: string;
  /** Já traduzido: Direção, Roteiro, Criação… */
  job: string;
  photoUrl: string | null;
}

export interface Company {
  name: string;
  logoUrl: string | null;
}

export interface Country {
  code: string;
  name: string | null;
}

export interface Provider {
  name: string;
  logoUrl: string | null;
}

/** Onde assistir no Brasil (dados JustWatch via TMDB). */
export interface WatchProviders {
  link: string | null;
  streaming: Provider[];
  free: Provider[];
  ads: Provider[];
  rent: Provider[];
  buy: Provider[];
}

export interface TitleDetail extends TitleCard {
  originalTitle: string | null;
  tagline: string | null;
  releaseDate: string | null;
  status: string | null;
  originalLanguage: string | null;
  episodesCount: number | null;
  voteCount: number | null;
  budget: number | null;
  revenue: number | null;
  trailerUrl: string | null;
  cast: CastMember[];
  crew: CrewMember[];
  productionCompanies: Company[];
  productionCountries: Country[];
  networks: Company[];
  watchProviders: WatchProviders;
  similar: TitleCard[];
}

export interface HomeHero extends TitleCard {
  tagline: string | null;
  trailerUrl: string | null;
}

export type RowKind = 'TOP10' | 'SIMILAR' | 'BINGE' | 'GENRE' | 'TOP_RATED';

export interface HomeRow {
  key: string;
  kind: RowKind;
  title: string;
  items: TitleCard[];
}

export interface HomeResponse {
  hero: HomeHero | null;
  rows: HomeRow[];
}

export type MatchKind = 'TITLE' | 'PERSON' | 'GENRE';

export interface SearchHit extends TitleCard {
  matchedBy: { kind: MatchKind; label: string | null };
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CursorPage<T> {
  items: T[];
  /** null = acabou */
  nextCursor: string | null;
}

export type TitleSort = 'POPULARITY' | 'RATING' | 'RELEASE';

/** Erro da API no formato ProblemDetail (RFC 9457). */
export interface ApiProblem {
  status: number;
  title?: string;
  detail?: string;
}
