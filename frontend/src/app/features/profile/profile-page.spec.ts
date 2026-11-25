import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { Profile, ProfileActivity } from '../../core/api/models';
import { ProfilePage } from './profile-page';

const user = { id: 1, username: 'jotave', displayName: 'Jotave', color: 0, mascot: 2 };
const profile: Profile = {
  user,
  email: 'jotave@exemplo.com',
  memberSince: `${new Date().getFullYear()}-11-25T12:00:00Z`,
  favoriteGenre: { id: 18, name: 'Drama', slug: 'drama' },
  favoriteTitle: null,
  stats: { likes: 12, matches: 3, rooms: 4, votes: 40 },
};

describe('ProfilePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 'tok', user }));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => localStorage.clear());

  async function render(activity: ProfileActivity) {
    const fixture = TestBed.createComponent(ProfilePage);
    await fixture.whenStable();
    http.expectOne('/api/me/profile').flush(profile);
    http.expectOne('/api/me/profile/activity').flush(activity);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('mostra quem é, os números, as semanas e o link para os ajustes', async () => {
    const weeklyLikes = [0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 4];
    const el = await render({ weeklyLikes, recentLikes: [] });
    expect(el.querySelector('h1')!.textContent).toContain('Jotave');
    expect(el.querySelector('.meta')!.textContent).toContain('desde 25 de nov');
    expect(el.querySelector('.bio')!.textContent).toContain('Ama drama');
    expect(el.querySelector('.stats')!.textContent).toContain('40');
    const cells = el.querySelectorAll('.weeks li');
    expect(cells.length).toBe(12);
    expect(cells[11].getAttribute('data-level')).toBe('4');
    expect(cells[8].getAttribute('data-level')).toBe('1');
    expect(cells[0].getAttribute('data-level')).toBe('0');
    expect(el.querySelector('a.edit')!.getAttribute('href')).toBe('/ajustes');
    expect(el.textContent).toContain('você ainda não curtiu nada.');
  });
});
