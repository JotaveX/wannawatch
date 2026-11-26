import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { detail } from '../../../testing/fixtures';
import { DetailsPage } from './details-page';

describe('DetailsPage', () => {
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

  async function render(id: string, mediaType: 'MOVIE' | 'SERIES' = 'MOVIE') {
    const fixture = TestBed.createComponent(DetailsPage);
    fixture.componentRef.setInput('id', id);
    fixture.componentRef.setInput('mediaType', mediaType);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('mostra título, elenco, equipe, onde assistir com crédito e parecidos', async () => {
    const { fixture, el } = await render('79');
    http.expectOne('/api/titles/79').flush(detail());
    await fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Interestelar');
    expect(el.textContent).toContain('Matthew McConaughey');
    expect(el.querySelector('.no-photo')?.textContent).toBe('MM');
    expect(el.textContent).toContain('Christopher Nolan');
    const where = el.querySelector('#onde-assistir')!;
    expect(where.textContent).toContain('Amazon Prime Video');
    expect(where.querySelector('a[href="https://www.justwatch.com"]')).not.toBeNull();
    expect(el.querySelector('a[href="/filme/41"]')).not.toBeNull();
    expect(document.title).toBe('Interestelar · wannawatch?');
  });

  it('sem login, "Já vi" leva para o login e volta para a página', async () => {
    const router = TestBed.inject(Router);
    const go = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/filme/79');
    const { fixture, el } = await render('79');
    http.expectOne('/api/titles/79').flush(detail());
    await fixture.whenStable();
    [...el.querySelectorAll<HTMLButtonElement>('.actions button')]
      .find((b) => b.textContent?.includes('Já vi'))!
      .click();
    expect(router.serializeUrl(go.mock.calls[0][0] as UrlTree)).toBe('/entrar?volta=%2Ffilme%2F79');
    http.expectNone((r) => r.url.includes('/signals'));
  });

  it('"Já vi" é um botão de alternar que vai para o back e desfaz se ele recusar', async () => {
    localStorage.setItem(
      'ww.session',
      JSON.stringify({ token: 't', user: { id: 1, username: 'jotave', displayName: 'Jotave' } }),
    );
    const { fixture, el } = await render('79');
    http.expectOne('/api/titles/79').flush(detail());
    await fixture.whenStable();
    const seen = [...el.querySelectorAll<HTMLButtonElement>('.actions button')].find((b) =>
      b.textContent?.includes('Já vi'),
    )!;
    expect(seen.getAttribute('aria-pressed')).toBe('false');

    seen.click();
    await fixture.whenStable();
    expect(seen.getAttribute('aria-pressed')).toBe('true');
    const put = http.expectOne('/api/titles/79/signals/SEEN?source=DETAILS');
    expect(put.request.method).toBe('PUT');
    put.flush(null, { status: 500, statusText: 'Erro' });
    await fixture.whenStable();
    expect(seen.getAttribute('aria-pressed')).toBe('false');
  });

  it('esconde o trailer e avisa quando não há onde assistir', async () => {
    const { fixture, el } = await render('79');
    const empty = { link: null, streaming: [], free: [], ads: [], rent: [], buy: [] };
    http.expectOne('/api/titles/79').flush(detail({ trailerUrl: null, watchProviders: empty }));
    await fixture.whenStable();
    expect(el.textContent).not.toContain('Assistir trailer');
    expect(el.querySelector('#onde-assistir')?.textContent).toContain(
      'não está em nenhum serviço no Brasil',
    );
  });

  it('mostra "não encontrado" para 404 e para id inválido', async () => {
    const a = await render('79');
    http.expectOne('/api/titles/79').flush(null, { status: 404, statusText: 'Not Found' });
    await a.fixture.whenStable();
    expect(a.el.querySelector('h1')?.textContent).toContain('Não achamos esse título');

    const b = await render('abc');
    http.expectNone('/api/titles/abc');
    expect(b.el.querySelector('h1')?.textContent).toContain('Não achamos esse título');
  });

  it('redireciona /filme/:id para /serie/:id quando o título é série', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture } = await render('79', 'MOVIE');
    http.expectOne('/api/titles/79').flush(detail({ mediaType: 'SERIES' }));
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/serie', '79'], { replaceUrl: true });
  });
});
