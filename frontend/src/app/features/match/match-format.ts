import { Room } from '../../core/api/models';

/** "Filmes · Drama", "Séries", "Filmes e séries" */
export function roomFilters(room: Pick<Room, 'mediaType' | 'genre'>): string {
  const type = room.mediaType === 'MOVIE' ? 'Filmes' : room.mediaType === 'SERIES' ? 'Séries' : 'Filmes e séries';
  return room.genre ? `${type} · ${room.genre.name}` : type;
}

/** Nome da sala: o nome de quem é dono dela ("Jotave"); o subtítulo é {@link roomFilters}. */
export function roomName(room: Pick<Room, 'owner'>): string {
  return room.owner.displayName;
}

/** "1 pessoa", "3 pessoas" */
export function peopleCount(n: number): string {
  return `${n} ${n === 1 ? 'pessoa' : 'pessoas'}`;
}

/** "Você, Bia e Caio" (quem está logado vem primeiro, como "Você") */
export function participantNames(room: Pick<Room, 'participants'>, meId: number | undefined): string {
  const names = [...room.participants]
    .sort((a, b) => Number(b.user.id === meId) - Number(a.user.id === meId))
    .map((p) => (p.user.id === meId ? 'Você' : p.user.displayName));
  if (names.length <= 1) return names[0] ?? '';
  return `${names.slice(0, -1).join(', ')} e ${names.at(-1)}`;
}
