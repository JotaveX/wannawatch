import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_BASE_URL } from '../api/api-config';
import { authInterceptor } from './auth-interceptor';
import { AuthStore, initialsOf } from './auth-store';

const user = { id: 2, username: 'bia', displayName: 'Bia Rocha' };

describe('AuthStore + authInterceptor', () => {
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthStore);
  }

  afterEach(() => localStorage.clear());

  it('entra, guarda a sessão e manda o token nas chamadas à API (só nelas)', () => {
    const auth = setup();
    auth.login('bia', 'wannawatch').subscribe();
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ username: 'bia', password: 'wannawatch' });
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush({ token: 'tok', user });

    expect(auth.user()).toEqual(user);
    expect(auth.initials()).toBe('BR');
    expect(JSON.parse(localStorage.getItem('ww.session')!).token).toBe('tok');

    const client = TestBed.inject(HttpClient);
    client.get('/api/auth/me').subscribe();
    expect(http.expectOne('/api/auth/me').request.headers.get('Authorization')).toBe('Bearer tok');
    client.get('https://image.tmdb.org/x.jpg').subscribe();
    expect(http.expectOne('https://image.tmdb.org/x.jpg').request.headers.has('Authorization')).toBe(false);
  });

  it('lê a sessão salva ao abrir o app', () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 'tok', user }));
    expect(setup().loggedIn()).toBe(true);
  });

  it('ignora sessão salva corrompida', () => {
    localStorage.setItem('ww.session', '{nada');
    expect(setup().loggedIn()).toBe(false);
  });

  it('401 da API esquece a sessão e manda para o login com a volta', async () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 'velho', user }));
    const auth = setup();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/match/ABC123');

    TestBed.inject(HttpClient).get('/api/likes').subscribe({ error: () => undefined });
    http.expectOne('/api/likes').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.loggedIn()).toBe(false);
    expect(localStorage.getItem('ww.session')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/entrar'], { queryParams: { volta: '/match/ABC123' } });
  });

  it('sair avisa o back e limpa a sessão', () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 'tok', user }));
    const auth = setup();
    auth.logout();
    expect(http.expectOne('/api/auth/logout').request.headers.get('Authorization')).toBe('Bearer tok');
    expect(auth.user()).toBeNull();
  });
});

describe('initialsOf', () => {
  it('usa primeira e última palavra, ou as duas primeiras letras', () => {
    expect(initialsOf('Bia Rocha')).toBe('BR');
    expect(initialsOf('Ana Maria Souza')).toBe('AS');
    expect(initialsOf('jotave')).toBe('JO');
    expect(initialsOf('  ')).toBe('?');
  });
});
