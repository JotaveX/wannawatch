import { confetti } from './match-celebration';

describe('confetti', () => {
  it('sai do ponto de origem e voa para cima, com duas levas', () => {
    let seed = 1;
    const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const pieces = confetti({ x: 200, y: 300 }, 400, 40, random);
    expect(pieces.length).toBe(40);
    expect(pieces.every((p) => p.x === 200 && p.y === 300)).toBe(true);
    expect(pieces.every((p) => p.dy < 0)).toBe(true);
    expect(pieces.some((p) => p.delay >= 380)).toBe(true);
  });
});
