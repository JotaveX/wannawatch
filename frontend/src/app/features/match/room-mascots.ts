export interface RoomMascot {
  src: string;
  /** cor de fundo da imagem (usada no brilho atrás dela) */
  color: string;
}

/**
 * Mascotes das salas de Match. O back sorteia um índice ao criar a sala (MatchService.MASCOTS
 * precisa ter o mesmo tamanho desta lista); o front só mostra a imagem.
 */
export const ROOM_MASCOTS: readonly RoomMascot[] = [
  { src: 'mascot/rooms/sala-0-pensando.svg', color: '#716dca' },
  { src: 'mascot/rooms/sala-1-pipoca.svg', color: '#2f8f83' },
  { src: 'mascot/rooms/sala-2-coracao.svg', color: '#f07a9a' },
  { src: 'mascot/rooms/sala-3-estrelas.svg', color: '#2e2b63' },
  { src: 'mascot/rooms/sala-4-claquete.svg', color: '#f29e4c' },
  { src: 'mascot/rooms/sala-5-sono.svg', color: '#5b8def' },
  { src: 'mascot/rooms/sala-6-ingresso.svg', color: '#3f9e7d' },
  { src: 'mascot/rooms/sala-7-esperando.svg', color: '#9b5de5' },
];

/** Mascote da sala; índice fora da lista cai num válido. */
export function roomMascot(index: number): RoomMascot {
  const n = ROOM_MASCOTS.length;
  return ROOM_MASCOTS[((Math.trunc(index) % n) + n) % n];
}
