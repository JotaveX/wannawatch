import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { Profile } from '../../core/api/models';
import { AuthStore } from '../../core/auth/auth-store';
import { ProfilePage } from './profile-page';

const user = { id: 1, username: 'jotave', displayName: 'Jotave', color: 0, mascot: null };
const profile: Profile = {
  user,
  email: 'jotave@exemplo.com',
  favoriteGenre: null,
  favoriteTitle: null,
  stats: { likes: 12, matches: 3 },
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

  async function render() {
    const fixture = TestBed.createComponent(ProfilePage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/genres').flush([
      { id: 18, name: 'Drama', slug: 'drama', forMovie: true, forSeries: true, titleCount: 5 },
    ]);
    http.expectOne('/api/me/profile').flush(profile);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const save = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    return { fixture, el, save };
  }

  it('mostra o perfil, a conta e os números', async () => {
    const { el, save } = await render();
    expect(el.querySelector<HTMLInputElement>('#nome')!.value).toBe('Jotave');
    expect(el.textContent).toContain('jotave@exemplo.com');
    expect(el.querySelector('.stats')!.textContent).toContain('12');
    expect(el.querySelectorAll('input[name="cor"]').length).toBe(8);
    // 1 de iniciais + 8 poses do Bu (sem foto ainda, a foto é um botão de enviar)
    expect(el.querySelectorAll('input[name="avatar"]').length).toBe(9);
    expect(save().disabled).toBe(true);
  });

  it('prévia ao vivo e salvar só com mudança; salva e atualiza a sessão', async () => {
    const { fixture, el, save } = await render();
    const name = el.querySelector<HTMLInputElement>('#nome')!;
    name.value = '  João   Victor ';
    name.dispatchEvent(new Event('input'));
    el.querySelectorAll<HTMLInputElement>('input[name="avatar"]')[3].click(); // Bu índice 2
    el.querySelectorAll<HTMLInputElement>('input[name="cor"]')[5].click();
    await fixture.whenStable();

    expect(el.querySelector('.head strong')!.textContent).toBe('João Victor');
    expect(save().disabled).toBe(false);

    save().click();
    const req = http.expectOne('/api/me/profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      displayName: 'João Victor',
      color: 5,
      mascot: 2,
      favoriteGenreId: null,
      favoriteTitleId: null,
    });
    const saved = { ...profile, user: { ...user, displayName: 'João Victor', color: 5, mascot: 2 } };
    req.flush(saved);
    await fixture.whenStable();

    expect(TestBed.inject(AuthStore).user()).toEqual(saved.user);
    expect(el.textContent).toContain('Perfil salvo.');
    expect(save().disabled).toBe(true);
  });

  it('não deixa salvar com o nome vazio', async () => {
    const { fixture, el, save } = await render();
    const name = el.querySelector<HTMLInputElement>('#nome')!;
    name.value = '   ';
    name.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(el.querySelector('#nome-erro')!.textContent).toContain('não pode ficar vazio');
    expect(save().disabled).toBe(true);
  });
});
