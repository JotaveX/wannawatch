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
export function length(card: Pick<TitleCard, 'mediaType' | 'runtimeMinutes' | 'seasonsCount'>): string | null {
  return card.mediaType === 'MOVIE' ? duration(card.runtimeMinutes) : seasons(card.seasonsCount);
}

/** "Filme · Ficção científica · 2h10" — partes vazias somem. */
export function metaLine(card: TitleCard): string {
  return [typeLabel(card), card.genres[0]?.name, length(card)].filter(Boolean).join(' · ');
}
