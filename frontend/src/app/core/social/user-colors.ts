import { UserRef } from '../api/models';

export interface UserColor {
  bg: string;
  fg: string;
  /** nome da cor, para leitores de tela */
  name: string;
}

/**
 * Cores que cada pessoa escolhe no perfil (esfera do avatar), com texto em contraste ≥ 4,5:1.
 * O back guarda o índice (ProfileService.COLORS = tamanho desta lista). Os 5 primeiros são as
 * cores que as contas já tinham antes de dar para escolher: não mude a ordem.
 */
export const USER_COLORS: readonly UserColor[] = [
  { bg: '#FFC300', fg: '#141416', name: 'Amarelo' },
  { bg: '#5E5CE6', fg: '#FFFFFF', name: 'Índigo' },
  { bg: '#FF2D55', fg: '#141416', name: 'Rosa' },
  { bg: '#30D158', fg: '#141416', name: 'Verde' },
  { bg: '#64D2FF', fg: '#141416', name: 'Azul' },
  { bg: '#FF9F0A', fg: '#141416', name: 'Laranja' },
  { bg: '#BF5AF2', fg: '#141416', name: 'Roxo' },
  { bg: '#00C7BE', fg: '#141416', name: 'Turquesa' },
];

/** Cor escolhida; sem escolha (dados antigos) cai numa fixa pelo id. */
export function userColors(user: Pick<UserRef, 'id' | 'color'>): UserColor {
  const n = USER_COLORS.length;
  const index = user.color ?? user.id - 1;
  return USER_COLORS[((index % n) + n) % n];
}
