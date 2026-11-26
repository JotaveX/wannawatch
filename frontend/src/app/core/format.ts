import { TitleCard } from './api/models';

/** 130 → "2h10" · 45 → "45 min" · 120 → "2h" */
export function duration(minutes: number | null | undefined): string | null {
  if (!minutes || minutes <= 0) return null;
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m === 0 ? `${h}h` : `${h}h${String(m).padStart(2, '0')}`;
}

/** 1 → "1 temporada" · 3 → "3 temporadas" */
export function seasons(count: number | null | undefined): string | null {
  if (!count || count <= 0) return null;
  return count === 1 ? '1 temporada' : `${count} temporadas`;
}

export function typeLabel(card: Pick<TitleCard, 'mediaType'>): string {
  return card.mediaType === 'MOVIE' ? 'Filme' : 'Série';
}

/** Duração do filme ou número de temporadas da série. */
export function length(
  card: Pick<TitleCard, 'mediaType' | 'runtimeMinutes' | 'seasonsCount'>,
): string | null {
  return card.mediaType === 'MOVIE' ? duration(card.runtimeMinutes) : seasons(card.seasonsCount);
}

/** "Filme · Ficção científica · 2h10" — partes vazias somem. */
export function metaLine(card: TitleCard): string {
  return [typeLabel(card), card.genres[0]?.name, length(card)].filter(Boolean).join(' · ');
}

const regionNames = new Intl.DisplayNames('pt-BR', { type: 'region' });
const languageNames = new Intl.DisplayNames('pt-BR', { type: 'language' });

/** "GB" → "Reino Unido" (a TMDB manda o nome em inglês). */
export function countryName(code: string, fallback?: string | null): string {
  try {
    return regionNames.of(code.toUpperCase()) ?? fallback ?? code;
  } catch {
    return fallback ?? code;
  }
}

/** "en" → "Inglês" */
export function languageName(code: string | null | undefined): string | null {
  if (!code) return null;
  try {
    const name = languageNames.of(code);
    return name && name !== code ? name.charAt(0).toUpperCase() + name.slice(1) : null;
  } catch {
    return null;
  }
}

/** Valores da TMDB são em dólar: 165000000 → "US$ 165 mi" */
export function money(value: number | null | undefined): string | null {
  if (!value || value <= 0) return null;
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

/** "2014-11-04" → "4 de novembro de 2014" */
export function longDate(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d).toLocaleDateString('pt-BR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}
