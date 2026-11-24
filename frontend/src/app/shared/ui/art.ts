/**
 * Arte geométrica dos placeholders (cor chapada + círculos), usada quando o título
 * não tem imagem ou a imagem falha. A combinação é escolhida pelo id, então é estável.
 */
export interface Art {
  bg: string;
  shape: string;
  dot: string;
  /** cor de texto com contraste sobre bg */
  fg: string;
  /** top/left em % da área; size em % da menor dimensão (cqmin) */
  circle: { top: number; left: number; size: number };
  spot: { top: number; left: number; size: number };
}

const SETS = [
  { bg: '#5E5CE6', shape: '#FFC300', dot: '#FF2D55', fg: '#FFFFFF' },
  { bg: '#FFC300', shape: '#FF2D55', dot: '#1D1D1F', fg: '#141416' },
  { bg: '#2C2C2E', shape: '#FF2D55', dot: '#FFC300', fg: '#FFFFFF' },
  { bg: '#FF2D55', shape: '#1D1D1F', dot: '#FBFBFD', fg: '#141416' },
  { bg: '#F2F2F7', shape: '#5E5CE6', dot: '#FF2D55', fg: '#141416' },
  { bg: '#1D1D1F', shape: '#5E5CE6', dot: '#FFC300', fg: '#FFFFFF' },
] as const;

/**
 * As formas ficam na parte de cima (terminam antes de ~60% da altura),
 * deixando a faixa de baixo livre para o nome do título.
 */
const LAYOUTS = [
  { circle: { top: -15, left: 40, size: 90 }, spot: { top: 44, left: 12, size: 15 } },
  { circle: { top: -25, left: -25, size: 100 }, spot: { top: 12, left: 72, size: 14 } },
  { circle: { top: 5, left: 28, size: 70 }, spot: { top: 4, left: 8, size: 13 } },
  { circle: { top: -32, left: 48, size: 110 }, spot: { top: 30, left: 12, size: 15 } },
] as const;

export function artFor(seed: number): Art {
  const s = Math.abs(Math.trunc(seed));
  const set = SETS[s % SETS.length];
  const layout = LAYOUTS[(s * 3 + 1) % LAYOUTS.length];
  return { ...set, circle: { ...layout.circle }, spot: { ...layout.spot } };
}
