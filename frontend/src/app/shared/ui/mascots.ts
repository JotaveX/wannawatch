/** Uma pose do Bu, o fantasminha do wannawatch?. */
export interface BuMascot {
  src: string;
  /** cor de fundo da imagem (usada no brilho atrás dela e como fundo do avatar) */
  color: string;
  /** nome da pose, para leitores de tela ("Bu com pipoca") */
  label: string;
}

/**
 * Poses do Bu. Servem de mascote das salas de Match (o back sorteia um índice) e de avatar
 * no perfil. MatchService.MASCOTS e ProfileService.MASCOTS precisam ter o tamanho desta lista.
 */
export const BU_MASCOTS: readonly BuMascot[] = [
  { src: 'mascot/rooms/sala-0-pensando.svg', color: '#716dca', label: 'Bu pensando' },
  { src: 'mascot/rooms/sala-1-pipoca.svg', color: '#2f8f83', label: 'Bu com pipoca' },
  { src: 'mascot/rooms/sala-2-coracao.svg', color: '#f07a9a', label: 'Bu apaixonado' },
  { src: 'mascot/rooms/sala-3-estrelas.svg', color: '#2e2b63', label: 'Bu nas estrelas' },
  { src: 'mascot/rooms/sala-4-claquete.svg', color: '#f29e4c', label: 'Bu com claquete' },
  { src: 'mascot/rooms/sala-5-sono.svg', color: '#5b8def', label: 'Bu com sono' },
  { src: 'mascot/rooms/sala-6-ingresso.svg', color: '#3f9e7d', label: 'Bu com ingresso' },
  { src: 'mascot/rooms/sala-7-esperando.svg', color: '#9b5de5', label: 'Bu esperando' },
];

/** Pose do Bu; índice fora da lista cai num válido. */
export function buMascot(index: number): BuMascot {
  const n = BU_MASCOTS.length;
  return BU_MASCOTS[((Math.trunc(index) % n) + n) % n];
}

/** Mascote da sala de Match (mesma lista). */
export const roomMascot = buMascot;
