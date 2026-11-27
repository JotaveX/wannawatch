import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { Profile } from '../../core/api/models';
import { AuthStore } from '../../core/auth/auth-store';
import { SettingsPage } from './settings-page';

const user = { id: 1, username: 'jotave', displayName: 'Jotave', color: 0, mascot: null };
const profile: Profile = {
  user,
  email: 'jotave@exemplo.com',
  memberSince: '2026-11-25T12:00:00Z',
  favoriteGenre: null,
  favoriteTitle: null,
  stats: { likes: 12, matches: 3, rooms: 4, votes: 40 },
};

describe('SettingsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 'tok', user }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => localStorage.clear());

  async function render(saved: Profile = profile) {
    const fixture = TestBed.createComponent(SettingsPage);
    await fixture.whenStable();
    http
      .expectOne((r) => r.url === '/api/genres')
      .flush([
        { id: 18, name: 'Drama', slug: 'drama', forMovie: true, forSeries: true, titleCount: 5 },
      ]);
    http.expectOne('/api/me/profile').flush(saved);
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
    const saved = {
      ...profile,
      user: { ...user, displayName: 'João Victor', color: 5, mascot: 2 },
    };
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

  it('com foto salva, trocar pelas iniciais apaga a foto depois de salvar o perfil', async () => {
    const photoUrl = 'https://fotos.exemplo/avatars/1/a.jpg';
    const { fixture, el, save } = await render({ ...profile, user: { ...user, photoUrl } });
    const options = el.querySelectorAll<HTMLInputElement>('input[name="avatar"]');
    // 1 de iniciais + 8 poses do Bu + a foto
    expect(options.length).toBe(10);
    expect(el.querySelector<HTMLInputElement>('input[aria-label="Sua foto"]')!.checked).toBe(true);
    expect(el.querySelector<HTMLImageElement>('.head img')!.src).toBe(photoUrl);

    options[0].click(); // iniciais
    await fixture.whenStable();
    save().click();

    const put = http.expectOne('/api/me/profile');
    expect(put.request.method).toBe('PUT');
    put.flush({ ...profile, user: { ...user, photoUrl } });
    const del = http.expectOne('/api/me/profile/photo');
    expect(del.request.method).toBe('DELETE');
    del.flush({ ...profile, user: { ...user, photoUrl: null } });
    await fixture.whenStable();

    expect(TestBed.inject(AuthStore).user()?.photoUrl).toBeNull();
    expect(el.textContent).toContain('Perfil salvo.');
  });

  it('sem mexer na foto, salvar não envia nem apaga nada', async () => {
    const photoUrl = 'https://fotos.exemplo/avatars/1/a.jpg';
    const { fixture, el, save } = await render({ ...profile, user: { ...user, photoUrl } });
    const name = el.querySelector<HTMLInputElement>('#nome')!;
    name.value = 'Jota';
    name.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    save().click();

    http
      .expectOne('/api/me/profile')
      .flush({ ...profile, user: { ...user, displayName: 'Jota', photoUrl } });
    await fixture.whenStable();
    http.expectNone('/api/me/profile/photo');
    expect(TestBed.inject(AuthStore).user()?.photoUrl).toBe(photoUrl);
  });
});
