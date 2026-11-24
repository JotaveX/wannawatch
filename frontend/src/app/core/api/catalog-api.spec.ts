import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from './api-config';
import { CatalogApi } from './catalog-api';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test/api' },
      ],
    });
    api = TestBed.inject(CatalogApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('usa a raiz configurada e não manda filtros vazios', () => {
    api.titles({ genre: 'terror', type: undefined, page: 0 }).subscribe();
    const req = http.expectOne((r) => r.url === 'http://api.test/api/titles');
    expect(req.request.params.get('genre')).toBe('terror');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.has('type')).toBe(false);
    req.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });

  it('manda seed e cursor no deck do Swipe', () => {
    api.swipeDeck({ seed: 'abc', cursor: 'xyz', size: 10 }).subscribe();
    const req = http.expectOne((r) => r.url === 'http://api.test/api/swipe/deck');
    expect(req.request.params.get('seed')).toBe('abc');
    expect(req.request.params.get('cursor')).toBe('xyz');
    req.flush({ items: [], nextCursor: null });
  });

  it('ignora cursor nulo na primeira página do Reels', () => {
    api.reels({ cursor: null }).subscribe();
    const req = http.expectOne((r) => r.url === 'http://api.test/api/reels');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ items: [], nextCursor: null });
  });

  it('busca com q, página e tamanho', () => {
    api.search('nolan').subscribe();
    const req = http.expectOne((r) => r.url === 'http://api.test/api/search');
    expect(req.request.params.get('q')).toBe('nolan');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
  });
});
