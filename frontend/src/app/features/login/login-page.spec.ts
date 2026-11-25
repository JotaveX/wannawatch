import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { LoginPage, safeReturn } from './login-page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => localStorage.clear());

  async function render(volta?: string) {
    const fixture = TestBed.createComponent(LoginPage);
    if (volta) fixture.componentRef.setInput('volta', volta);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const type = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = async () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    return { fixture, el, type, submit };
  }

  it('pede usuário e senha antes de chamar o back', async () => {
    const { el, submit } = await render();
    await submit();
    expect(el.querySelector('[role="alert"]')?.textContent).toBe('Preencha usuário e senha.');
    http.expectNone('/api/auth/login');
  });

  it('senha errada mostra o erro', async () => {
    const { fixture, el, type, submit } = await render();
    type('username', 'bia');
    type('password', 'errada');
    await submit();
    http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toBe('Usuário ou senha incorretos.');
  });

  it('entra e volta para onde ia', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { type, submit } = await render('/match/K7M2QX');
    type('username', ' bia ');
    type('password', 'wannawatch');
    await submit();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body.username).toBe('bia');
    req.flush({ token: 't', user: { id: 2, username: 'bia', displayName: 'Bia' } });
    expect(navigate).toHaveBeenCalledWith('/match/K7M2QX');
  });
});

describe('safeReturn', () => {
  it('só aceita caminhos internos', () => {
    expect(safeReturn('/swipe')).toBe('/swipe');
    expect(safeReturn('//evil.com')).toBe('/');
    expect(safeReturn('https://evil.com')).toBe('/');
    expect(safeReturn(undefined)).toBe('/');
  });
});
