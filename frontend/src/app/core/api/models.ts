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
  /** Por que o título apareceu (só nas listas personalizadas; nas outras é null). */
  reason: Reason | null;
}

/** Vídeo curto aprovado de um título; toca pelo embed do YouTube (`key` = id do vídeo). */
export interface ReelVideo {
  platform: 'YOUTUBE';
  key: string;
}

/** Item do Reels: o card e, se o título tiver vídeo aprovado, o vídeo. */
export interface ReelCard extends TitleCard {
  video: ReelVideo | null;
}

export type ReasonKind = 'BECAUSE_LIKED' | 'PERSON' | 'GENRE' | 'GROUP' | 'WANTED_BY' | 'POPULAR';

/** Motivo da recomendação, com o texto pronto ("Porque você curtiu Dark", "Bom pra você e pra Bia"). */
export interface Reason {
  kind: ReasonKind;
  text: string;
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

export type RowKind = 'TOP10' | 'FOR_YOU' | 'SIMILAR' | 'BINGE' | 'GENRE' | 'TOP_RATED';

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

// --- Usuários, curtidas e Match (espelho de auth/, social/ e match/ no back) ---

export interface UserRef {
  id: number;
  username: string;
  displayName: string;
  /** índice de USER_COLORS (core/social/user-colors.ts); ausente = cor fixa pelo id */
  color?: number;
  /** índice de BU_MASCOTS (shared/ui/mascots.ts) usado como avatar; null = iniciais */
  mascot?: number | null;
}

export interface ProfileStats {
  likes: number;
  /** salas de Match que terminaram em match */
  matches: number;
  /** salas de Match em que entrou */
  rooms: number;
  /** votos dados nas salas de Match */
  votes: number;
}

/** GET/PUT /api/me/profile */
export interface Profile {
  user: UserRef;
  email: string | null;
  /** ISO, quando a conta foi criada */
  memberSince: string;
  favoriteGenre: GenreRef | null;
  favoriteTitle: TitleCard | null;
  stats: ProfileStats;
}

/** GET /api/me/profile/activity */
export interface ProfileActivity {
  /** curtidas por semana, da mais antiga (12 semanas atrás) para a atual */
  weeklyLikes: number[];
  /** últimos curtidos, do mais novo para o mais antigo */
  recentLikes: TitleCard[];
}

export interface ProfileUpdate {
  displayName: string;
  color: number;
  mascot: number | null;
  favoriteGenreId: number | null;
  favoriteTitleId: number | null;
}

/** Em que etapa do cadastro a conta está (espelho de auth/AccountStatus). */
export interface AccountStatus {
  email: string | null;
  emailVerified: boolean;
  /** já escolheu os títulos de que gosta (pelo menos 5) */
  titlesPicked: boolean;
  /** as duas etapas feitas: pode usar o app inteiro */
  complete: boolean;
  /** administrador: vê a revisão dos vídeos do Reels (/admin/videos) */
  admin: boolean;
}

export interface LoginResponse {
  token: string;
  user: UserRef;
  account: AccountStatus;
}

/** GET /api/auth/account */
export interface AccountResponse {
  user: UserRef;
  account: AccountStatus;
}

/** POST /api/auth/register */
export interface RegisterRequest {
  displayName: string;
  username: string;
  email: string;
  password: string;
}

/** Para um título: se eu curti e quais amigos (todos os outros usuários) curtiram. */
/** Sinais guardados no back além do "quero ver" (que fica nas curtidas). */
export type SignalKind = 'PASS' | 'MAYBE' | 'SEEN' | 'SAVED';

/** De que tela veio a interação (vai para o log de interações do back). */
export type InteractionSource = 'SWIPE' | 'REELS' | 'DETAILS' | 'HOME' | 'MATCH' | 'ONBOARDING';

export interface TitleSignals {
  titleId: number;
  kinds: SignalKind[];
}

export interface TitleLikes {
  titleId: number;
  likedByMe: boolean;
  friends: UserRef[];
}

export interface FriendsLiked {
  title: TitleCard;
  likedByMe: boolean;
  friends: UserRef[];
}

/** WAITING: esperando a galera (dá pra entrar); VOTING: votando; MATCHED: deu match. */
export type RoomStatus = 'WAITING' | 'VOTING' | 'MATCHED';

export interface RoomParticipant {
  user: UserRef;
  votes: number;
  likes: number;
  joinedAt: string;
}

export interface Room {
  code: string;
  mediaType: MediaType | null;
  genre: GenreRef | null;
  owner: UserRef;
  participants: RoomParticipant[];
  status: RoomStatus;
  /** índice do mascote sorteado para a sala (ver features/match/room-mascots.ts) */
  mascot: number;
  /** título em que todos deram "quero ver"; null enquanto a sala está aberta */
  winner: TitleCard | null;
  /** quando o dono começou a votação; null enquanto espera a galera */
  startedAt: string | null;
  matchedAt: string | null;
  createdAt: string;
}

// --- vídeos do Reels (teste com a API do YouTube; só admin) ---

export type VideoStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface TitleVideo {
  id: number;
  /** id do vídeo no YouTube */
  externalKey: string;
  videoTitle: string | null;
  channelTitle: string | null;
  publishedAt: string | null;
  durationSeconds: number | null;
  width: number | null;
  height: number | null;
  vertical: boolean;
  embeddable: boolean;
  /** bloqueado no Brasil */
  blockedInRegion: boolean;
  searchRank: number | null;
  status: VideoStatus;
  /** quando foi aprovado ou reprovado (null enquanto pendente) */
  reviewedAt: string | null;
}

export interface TitleVideoSearch {
  titleId: number;
  title: string;
  mediaType: MediaType;
  year: number | null;
  poster: ImageSet | null;
  query: string;
  resultsCount: number;
  searchedAt: string;
  videos: TitleVideo[];
}

/** De onde saem os títulos da busca de vídeos. */
export type DiscoverSource = 'POPULAR' | 'TRENDING' | 'TOP_RATED' | 'RECENT';

/** Filtros da busca de vídeos no YouTube (todos opcionais; o back tem os padrões). */
export interface DiscoverRequest {
  limit: number;
  /** pula títulos que já têm vídeo aprovado (padrão: sim) */
  skipApproved?: boolean;
  /** busca de novo títulos que já foram buscados (padrão: não) */
  includeSearched?: boolean;
  source?: DiscoverSource;
  type?: MediaType | null;
  /** slug do gênero */
  genre?: string | null;
  yearFrom?: number | null;
  yearTo?: number | null;
}

export interface DiscoverResult {
  titlesSearched: number;
  /** vídeos verticais guardados */
  videosFound: number;
  /** vídeos que a busca trouxe mas eram horizontais (descartados) */
  horizontalSkipped: number;
  /** preenchido quando a rodada parou no meio (ex.: cota do YouTube acabou) */
  error: string | null;
}
