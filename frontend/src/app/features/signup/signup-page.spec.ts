import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { AccountStatus } from '../../core/api/models';
import { requireSignupDone, signupPending } from '../../core/auth/auth-guard';
import { AuthStore } from '../../core/auth/auth-store';
import { card } from '../../../testing/fixtures';
import { accountErrors } from './signup-account-step';
import { SignupPage } from './signup-page';

const user = { id: 9, username: 'ana', displayName: 'Ana' };
const fresh: AccountStatus = {
  email: 'ana@exemplo.com',
  emailVerified: false,
  titlesPicked: false,
  complete: false,
};
const picked: AccountStatus = { ...fresh, titlesPicked: true };
const done: AccountStatus = { ...picked, emailVerified: true, complete: true };

/** espera o debounce da busca de títulos */
const debounce = () => new Promise((r) => setTimeout(r, 300));

describe('SignupPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
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

  async function render(volta?: string) {
    const fixture = TestBed.createComponent(SignupPage);
    if (volta) fixture.componentRef.setInput('volta', volta);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const type = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const button = (text: string) =>
      [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) =>
        b.textContent?.trim().startsWith(text),
      )!;
    const current = () => el.querySelector('[aria-current="step"]')?.textContent?.trim();
    return { fixture, el, type, button, current };
  }

  it('cria a conta e passa para a escolha dos títulos', async () => {
    const { fixture, el, type, current } = await render();
    expect(current()).toBe('1 Sua conta');

    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toBe('Confira os campos destacados.');
    expect(el.querySelector('input[name="email"]')?.getAttribute('aria-invalid')).toBe('true');
    http.expectNone('/api/auth/register');

    type('displayName', ' Ana ');
    type('username', 'Ana');
    type('email', 'ana@exemplo.com');
    type('password', 'senha-boa');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      displayName: 'Ana',
      username: 'ana',
      email: 'ana@exemplo.com',
      password: 'senha-boa',
    });
    req.flush({ token: 't', user, account: fresh });
    await fixture.whenStable();

    expect(current()).toContain('Do que você gosta');
    expect(el.querySelector('h1')?.textContent).toBe('Do que você gosta?');
  });

  it('mostra o erro do back (ex.: usuário repetido)', async () => {
    const { fixture, el, type } = await render();
    type('displayName', 'Ana');
    type('username', 'bia');
    type('email', 'ana@exemplo.com');
    type('password', 'senha-boa');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/auth/register')
      .flush(
        { status: 409, detail: 'Esse usuário já existe. Tente outro.' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toBe(
      'Esse usuário já existe. Tente outro.',
    );
  });

  it('pede pelo menos 5 títulos e manda o favorito junto', async () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 't', user, account: fresh }));
    const { fixture, el, button } = await render();
    await debounce();
    const popular = http.expectOne((r) => r.url === '/api/titles');
    expect(popular.request.params.get('type')).toBe('MOVIE');
    popular.flush({
      items: [1, 2, 3, 4, 5, 6].map((id) => card(id)),
      page: 0,
      size: 24,
      totalItems: 6,
      totalPages: 1,
    });
    await fixture.whenStable();

    const pick = (title: string) =>
      el.querySelector<HTMLButtonElement>(`.grid button[aria-label^="${title}"]`)!;
    pick('Título 1').click();
    await fixture.whenStable();
    expect(pick('Título 1').getAttribute('aria-pressed')).toBe('true');

    button('Continuar').click();
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toBe('Escolha mais 4 para continuar.');
    http.expectNone('/api/me/onboarding/titles');

    for (const n of [2, 3, 4, 5]) pick(`Título ${n}`).click();
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>(
      '[aria-label="Favorito de todos os tempos: Título 3"]',
    )!.click();
    await fixture.whenStable();
    button('Continuar').click();

    const req = http.expectOne('/api/me/onboarding/titles');
    expect(req.request.body).toEqual({ titleIds: [1, 2, 3, 4, 5], favoriteTitleId: 3 });
    req.flush(picked);
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toBe('Confirme seu e-mail');
    expect(el.textContent).toContain('ana@exemplo.com');
  });

  it('confirma o código e vai para onde ia', async () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 't', user, account: picked }));
    const go = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, type } = await render('/swipe');

    type('code', '12');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toBe('O código tem 6 números.');

    type('code', '123 456');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/auth/email/verify');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush(done);
    await fixture.whenStable();
    expect(TestBed.inject(AuthStore).ready()).toBe(true);
    expect(go).toHaveBeenCalledWith('/swipe');
  });
});

describe('accountErrors', () => {
  const ok = {
    displayName: 'Ana',
    username: 'ana.lima',
    email: 'ana@exemplo.com',
    password: 'senha-boa',
  };
  it('segue as regras do back', () => {
    expect(accountErrors(ok)).toEqual({});
    expect(Object.keys(accountErrors({ ...ok, username: 'an' }))).toEqual(['username']);
    expect(Object.keys(accountErrors({ ...ok, username: 'ana lima' }))).toEqual(['username']);
    expect(Object.keys(accountErrors({ ...ok, email: 'ana@exemplo' }))).toEqual(['email']);
    expect(Object.keys(accountErrors({ ...ok, password: '1234567' }))).toEqual(['password']);
    expect(Object.keys(accountErrors({ ...ok, displayName: '  ' }))).toEqual(['displayName']);
  });
});

describe('guards do cadastro', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });
  afterEach(() => localStorage.clear());

  /** a sessão precisa estar no localStorage antes da primeira chamada (o AuthStore lê ao nascer) */
  function run(guard: typeof requireSignupDone, url = '/swipe') {
    const result = TestBed.runInInjectionContext(() => guard(null!, { url } as never));
    return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;
  }

  it('sem login, o catálogo fica aberto e /cadastro também', () => {
    expect(run(requireSignupDone)).toBe(true);
    expect(run(signupPending)).toBe(true);
  });

  it('cadastro pela metade volta para /cadastro, guardando a volta', () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 't', user, account: picked }));
    expect(run(requireSignupDone, '/filme/7')).toBe('/cadastro?volta=%2Ffilme%2F7');
    expect(run(signupPending)).toBe(true);
  });

  it('cadastro completo segue normal e não volta para /cadastro', () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 't', user, account: done }));
    expect(run(requireSignupDone)).toBe(true);
    expect(run(signupPending)).toBe('/');
  });
});
