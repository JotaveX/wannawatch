import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-config';
import { DiscoverRequest, Genre } from '../../core/api/models';
import { DiscoverForm } from './discover-form';

const genres: Genre[] = [
  { id: 1, name: 'Drama', slug: 'drama', forMovie: true, forSeries: true, titleCount: 10 },
  { id: 2, name: 'Reality show', slug: 'reality', forMovie: false, forSeries: true, titleCount: 3 },
];

describe('DiscoverForm', () => {
  async function render() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    const fixture = TestBed.createComponent(DiscoverForm);
    TestBed.inject(HttpTestingController).expectOne('/api/genres').flush(genres);
    await fixture.whenStable();
    const form = fixture.componentInstance;
    const sent: DiscoverRequest[] = [];
    form.search.subscribe((r) => sent.push(r));
    return { fixture, form, sent, el: fixture.nativeElement as HTMLElement };
  }

  it('manda o padrão: pula quem já tem vídeo aprovado e só títulos nunca buscados', async () => {
    const { form, sent } = await render();
    form.submit();
    expect(sent).toEqual([
      {
        limit: 5,
        skipApproved: true,
        includeSearched: false,
        source: 'POPULAR',
        type: null,
        genre: null,
        yearFrom: null,
        yearTo: null,
      },
    ]);
    expect(form.activeFilters()).toBe(0);
  });

  it('abre a busca avançada e manda os filtros escolhidos', async () => {
    const { fixture, form, sent, el } = await render();
    const toggle = [...el.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Busca avançada'),
    )!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('#discover-advanced')).not.toBeNull();

    form.source.set('TRENDING');
    form.type.set('SERIES');
    form.genre.set('reality');
    form.yearFrom.set(2020);
    form.includeSearched.set(true);
    form.skipApproved.set(false);
    expect(form.activeFilters()).toBe(5);
    form.submit();
    expect(sent[0]).toMatchObject({
      skipApproved: false,
      includeSearched: true,
      source: 'TRENDING',
      type: 'SERIES',
      genre: 'reality',
      yearFrom: 2020,
      yearTo: null,
    });

    form.reset();
    expect(form.activeFilters()).toBe(0);
  });

  it('descarta gênero que não existe para o tipo e não busca com anos invertidos', async () => {
    const { form, sent } = await render();
    form.genre.set('reality');
    form.type.set('MOVIE');
    form.submit();
    expect(sent[0].genre).toBeNull();

    form.yearFrom.set(2024);
    form.yearTo.set(2020);
    form.submit();
    expect(sent.length).toBe(1);
  });
});
