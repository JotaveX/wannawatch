import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { SearchHit } from '../../core/api/models';
import { card } from '../../../testing/fixtures';
import { matchLabel, SearchPage } from './search-page';

const wait = (ms = 350) => new Promise((r) => setTimeout(r, ms));
const hit = (id: number, kind: SearchHit['matchedBy']['kind'], label: string | null): SearchHit => ({
  ...card(id),
  matchedBy: { kind, label },
});

describe('SearchPage', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  async function render(q?: string) {
    const fixture = TestBed.createComponent(SearchPage);
    if (q !== undefined) fixture.componentRef.setInput('q', q);
    await fixture.whenStable();
    http.expectOne('/api/genres').flush([{ id: 1, name: 'Terror', slug: 'terror', forMovie: true, forSeries: true, titleCount: 9 }]);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('explica por que cada resultado apareceu', () => {
    expect(matchLabel(hit(1, 'PERSON', 'com Pedro Pascal'))).toBe('com Pedro Pascal');
    expect(matchLabel(hit(1, 'GENRE', 'Terror'))).toBe('Gênero: Terror');
    expect(matchLabel(hit(1, 'TITLE', null))).toBeNull();
  });

  it('busca o termo da URL e mostra os resultados', async () => {
    const { fixture, el } = await render('nolan');
    await wait();
    const req = http.expectOne((r) => r.url === '/api/search');
    expect(req.request.params.get('q')).toBe('nolan');
    req.flush({ items: [hit(79, 'PERSON', 'Direção de Christopher Nolan')], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    await fixture.whenStable();

    expect(el.querySelector<HTMLInputElement>('input')!.value).toBe('nolan');
    expect(el.querySelector('.status')?.textContent).toContain('1 resultado para “nolan”');
    expect(el.querySelector('a[href="/filme/79"] .why')?.textContent).toBe('Direção de Christopher Nolan');
    expect(navigate).not.toHaveBeenCalled(); // a URL já estava certa
  });

  it('espera parar de digitar, busca uma vez só e atualiza a URL sem empilhar histórico', async () => {
    const { fixture, el } = await render();
    const input = el.querySelector<HTMLInputElement>('input')!;
    for (const value of ['p', 'pe', 'ped', 'pedro']) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await wait(50);
    }
    await wait();
    http.expectOne((r) => r.url === '/api/search' && r.params.get('q') === 'pedro').flush({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith([], { queryParams: { q: 'pedro' }, replaceUrl: true });
    expect(el.textContent).toContain('Nada encontrado para “pedro”');
  });

  it('com menos de 2 letras não busca e mostra os gêneros, que viram busca ao clicar', async () => {
    const { fixture, el } = await render('a');
    await wait();
    http.expectNone((r) => r.url === '/api/search');
    const chip = el.querySelector<HTMLButtonElement>('.chips button')!;
    expect(chip.textContent).toContain('Terror');

    chip.click();
    await wait();
    http.expectOne((r) => r.url === '/api/search' && r.params.get('q') === 'Terror').flush({
      items: [hit(5, 'GENRE', 'Terror')],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    await fixture.whenStable();
    expect(el.querySelector('.why')?.textContent).toBe('Gênero: Terror');
  });

  it('carrega a próxima página e junta os resultados', async () => {
    const { fixture, el } = await render('drama');
    await wait();
    http.expectOne((r) => r.url === '/api/search').flush({
      items: [hit(1, 'GENRE', 'Drama')],
      page: 0,
      size: 20,
      totalItems: 2,
      totalPages: 2,
    });
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('button.more')!.click();
    const next = http.expectOne((r) => r.url === '/api/search' && r.params.get('page') === '1');
    next.flush({ items: [hit(2, 'GENRE', 'Drama')], page: 1, size: 20, totalItems: 2, totalPages: 2 });
    await fixture.whenStable();
    expect(el.querySelectorAll('.results li').length).toBe(2);
    expect(el.querySelector('button.more')).toBeNull();
  });
});
