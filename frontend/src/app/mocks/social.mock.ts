/**
 * ⚠️ MOCK — dados sociais de mentira.
 *
 * Ainda não existem usuários, grupos nem algoritmo de recomendação. Tudo que é "social"
 * nas telas (% match, grupo, quem quer ver, continue assistindo) sai daqui, e só daqui.
 * Quando o back-end tiver isso, troque a implementação deste serviço e as telas continuam iguais.
 *
 * Os números são derivados do id do título, então são estáveis entre recarregamentos.
 */
import { computed, Injectable, signal } from '@angular/core';
import { TitleCard } from '../core/api/models';

export type ViewMode = 'group' | 'solo';

export interface Friend {
  name: string;
  initials: string;
  /** cor da esfera (paleta) e do texto por cima, com contraste */
  bg: string;
  fg: string;
}

export interface ContinueWatching {
  title: TitleCard;
  /** 0–100 */
  progress: number;
  /** ex.: "T2:E4 · faltam 18 min" */
  label: string;
}

@Injectable({ providedIn: 'root' })
export class SocialMock {
  readonly user = { initials: 'JV' } as const;
  readonly group = { name: 'Sexta do cinema', members: 4 } as const;

  /** Amigos do grupo (o grupo tem 4 pessoas: você + 3). Sem foto ainda: a esfera mostra as iniciais. */
  readonly friends: readonly Friend[] = [
    { name: 'Bia Rocha', initials: 'BR', bg: '#5E5CE6', fg: '#FFFFFF' },
    { name: 'Caio Lima', initials: 'CL', bg: '#FFC300', fg: '#141416' },
    { name: 'Duda Alves', initials: 'DA', bg: '#FF2D55', fg: '#141416' },
  ];

  /** Seletor Grupo | Solo — compartilhado entre as telas. */
  readonly mode = signal<ViewMode>('group');
  readonly isGroup = computed(() => this.mode() === 'group');

  /** "92% match": entre 70 e 98, fixo para cada título. */
  matchPercent(titleId: number): number {
    return 70 + (hash(titleId, 1) % 29);
  }

  /** Quantos amigos do grupo querem ver (1..membros). */
  friendsWhoWant(titleId: number): number {
    return 1 + (hash(titleId, 2) % this.group.members);
  }

  /** Quais amigos também curtiram o título (0 a 3, fixo por título). */
  friendsWhoLiked(titleId: number): Friend[] {
    const count = hash(titleId, 8) % (this.friends.length + 1);
    const start = hash(titleId, 9) % this.friends.length;
    return Array.from({ length: count }, (_, i) => this.friends[(start + i) % this.friends.length]);
  }

  /** "Bia também curtiu" · "Bia e Caio também curtiram" · "Bia e mais 2 também curtiram" */
  likedByLabel(friends: readonly Friend[]): string | null {
    const first = (f: Friend) => f.name.split(' ')[0];
    if (friends.length === 0) return null;
    if (friends.length === 1) return `${first(friends[0])} também curtiu`;
    if (friends.length === 2) return `${first(friends[0])} e ${first(friends[1])} também curtiram`;
    return `${first(friends[0])} e mais ${friends.length - 1} também curtiram`;
  }

  /** Contador do "quero ver" do Reels, ex.: "12,4 mil". */
  wantCount(titleId: number): string {
    const n = 800 + (hash(titleId, 3) % 24_000);
    return n >= 1000
      ? `${(n / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`
      : String(n);
  }

  /** Transforma títulos reais em itens de "Continue assistindo" com progresso falso. */
  continueWatching(titles: TitleCard[]): ContinueWatching[] {
    return titles.map((title) => {
      const progress = 10 + (hash(title.id, 4) % 80);
      const left = 5 + (hash(title.id, 5) % 55);
      const prefix =
        title.mediaType === 'SERIES'
          ? `T${1 + (hash(title.id, 6) % Math.max(1, title.seasonsCount ?? 1))}:E${1 + (hash(title.id, 7) % 9)} · `
          : '';
      return { title, progress, label: `${prefix}faltam ${left} min` };
    });
  }
}

/** Hash inteiro simples e determinístico. */
function hash(id: number, salt: number): number {
  let h = (id * 2654435761 + salt * 40503) >>> 0;
  h ^= h >>> 15;
  h = Math.imul(h, 2246822519) >>> 0;
  h ^= h >>> 13;
  return h >>> 0;
}
