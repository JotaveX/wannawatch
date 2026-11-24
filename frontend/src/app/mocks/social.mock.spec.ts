import { TestBed } from '@angular/core/testing';
import { TitleCard } from '../core/api/models';
import { SocialMock } from './social.mock';

const card = (id: number, mediaType: TitleCard['mediaType'] = 'MOVIE'): TitleCard => ({
  id,
  mediaType,
  title: `Título ${id}`,
  year: 2026,
  genres: [],
  overview: null,
  runtimeMinutes: null,
  seasonsCount: mediaType === 'SERIES' ? 3 : null,
  ageRating: null,
  voteAverage: null,
  poster: null,
  backdrop: null,
});

describe('SocialMock', () => {
  let social: SocialMock;

  beforeEach(() => (social = TestBed.inject(SocialMock)));

  it('dá o mesmo % de match para o mesmo título, sempre entre 70 e 98', () => {
    for (let id = 1; id < 500; id++) {
      const m = social.matchPercent(id);
      expect(m).toBe(social.matchPercent(id));
      expect(m).toBeGreaterThanOrEqual(70);
      expect(m).toBeLessThanOrEqual(98);
    }
  });

  it('nunca diz que mais amigos querem ver do que o grupo tem', () => {
    for (let id = 1; id < 200; id++) {
      const n = social.friendsWhoWant(id);
      expect(n).toBeGreaterThanOrEqual(1);
      expect(n).toBeLessThanOrEqual(social.group.members);
    }
  });

  it('monta o "continue assistindo" com temporada e episódio só para séries', () => {
    const [movie, series] = social.continueWatching([card(1), card(2, 'SERIES')]);
    expect(movie.label).toMatch(/^faltam \d+ min$/);
    expect(series.label).toMatch(/^T[1-3]:E\d · faltam \d+ min$/);
    expect(movie.progress).toBeGreaterThan(0);
    expect(movie.progress).toBeLessThan(100);
  });

  it('começa no modo grupo', () => {
    expect(social.isGroup()).toBe(true);
    social.mode.set('solo');
    expect(social.isGroup()).toBe(false);
  });
});
