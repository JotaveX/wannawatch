import { CastMember, CrewMember, TitleDetail, WatchProviders, Provider } from '../../core/api/models';
import { countryName, languageName, longDate, money, seasons } from '../../core/format';

/** Funções puras que preparam o detalhe para a tela (testáveis sem DOM). */

export interface InfoItem {
  label: string;
  value: string;
}

/** Ficha técnica: só entra o que existe. */
export function infoItems(d: TitleDetail): InfoItem[] {
  const items: (InfoItem | null)[] = [
    d.originalTitle && d.originalTitle !== d.title ? { label: 'Título original', value: d.originalTitle } : null,
    item(d.mediaType === 'MOVIE' ? 'Lançamento' : 'Estreia', longDate(d.releaseDate)),
    item('Status', d.status),
    item('Idioma original', languageName(d.originalLanguage)),
    d.mediaType === 'SERIES' ? item('Temporadas', seasons(d.seasonsCount)) : null,
    d.mediaType === 'SERIES' && d.episodesCount ? item('Episódios', String(d.episodesCount)) : null,
    item('Orçamento', money(d.budget)),
    item('Bilheteria', money(d.revenue)),
    item('Emissoras', d.networks.map((n) => n.name).join(', ')),
    item('Produção', d.productionCompanies.map((c) => c.name).join(', ')),
    item('Países', d.productionCountries.map((c) => countryName(c.code, c.name)).join(', ')),
    d.voteAverage && d.voteCount
      ? item('Nota na TMDB', `${d.voteAverage.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} (${d.voteCount.toLocaleString('pt-BR')} votos)`)
      : null,
  ];
  return items.filter((i): i is InfoItem => i !== null);
}

export interface CrewGroup {
  job: string;
  names: string[];
}

/** Junta a equipe por função, mantendo a ordem em que a função aparece. */
export function crewGroups(crew: CrewMember[]): CrewGroup[] {
  const groups = new Map<string, string[]>();
  for (const c of crew) {
    const names = groups.get(c.job) ?? [];
    if (!names.includes(c.name)) names.push(c.name);
    groups.set(c.job, names);
  }
  return [...groups].map(([job, names]) => ({ job, names }));
}

export interface ProviderGroup {
  label: string;
  providers: Provider[];
}

/** Onde assistir, por tipo de acesso, sem grupos vazios. */
export function providerGroups(w: WatchProviders): ProviderGroup[] {
  return [
    { label: 'Assinatura', providers: w.streaming },
    { label: 'Grátis', providers: w.free },
    { label: 'Com anúncios', providers: w.ads },
    { label: 'Aluguel', providers: w.rent },
    { label: 'Compra', providers: w.buy },
  ].filter((g) => g.providers.length > 0);
}

/** "Matthew McConaughey" → "MM" (quando não há foto) */
export function initials(person: Pick<CastMember, 'name'>): string {
  const parts = person.name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
  return (first + last).toUpperCase();
}

function item(label: string, value: string | null | undefined): InfoItem | null {
  return value ? { label, value } : null;
}
