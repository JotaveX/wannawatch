/** Um pedacinho de confete: sai do centro da carta, abre em leque e cai. */
export interface Confetto {
  /** ponto de partida (px da janela) */
  x: number;
  y: number;
  /** até onde voa no impulso (px) */
  dx: number;
  dy: number;
  /** giro total (graus) */
  rotate: number;
  w: number;
  h: number;
  round: boolean;
  color: string;
  delay: number;
  duration: number;
}

const COLORS = [
  'var(--ww-pink)',
  'var(--ww-yellow)',
  'var(--ww-indigo)',
  'var(--ww-mascot-pink)',
  '#ffffff',
];

/** Gera o confete de uma explosão a partir de `origin`. `random` existe para os testes. */
export function confetti(
  origin: { x: number; y: number },
  viewportWidth: number,
  count = 90,
  random: () => number = Math.random,
): Confetto[] {
  const spread = Math.min(viewportWidth, 960) * 0.55;
  return Array.from({ length: count }, (_, i) => {
    // leque para cima, mais forte no meio
    const angle = -Math.PI / 2 + (random() - 0.5) * Math.PI * 1.15;
    const force = 0.35 + random() * 0.65;
    const round = random() < 0.3;
    const size = 6 + random() * 7;
    return {
      x: origin.x,
      y: origin.y,
      dx: Math.round(Math.cos(angle) * spread * force),
      dy: Math.round(Math.sin(angle) * spread * 0.9 * force - 40),
      rotate: Math.round((random() - 0.5) * 1440),
      w: round ? size : size * 0.6,
      h: round ? size : size * 1.5,
      round,
      color: COLORS[i % COLORS.length],
      // segunda leva sai um pouco depois, como uma segunda explosão
      delay: i < count * 0.65 ? Math.round(random() * 90) : 380 + Math.round(random() * 120),
      duration: 2100 + Math.round(random() * 900),
    };
  });
}

export function prefersReducedMotion(doc: Document): boolean {
  return doc.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}
