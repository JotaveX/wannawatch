import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-config';
import { card } from '../../../testing/fixtures';
import { ReelsStore } from './reels-store';

const range = (from: number, n: number) => Array.from({ length: n }, (_, i) => card(from + i));

describe('ReelsStore', () => {
  let store: ReelsStore;
  let http: HttpTestingController;
  const reels = () => http.expectOne((r) => r.url === '/api/reels');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ReelsStore, provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    store = TestBed.inject(ReelsStore);
    http = TestBed.inject(HttpTestingController);
  });

  it('a primeira página vai sem cursor', () => {
    store.load();
    const first = reels();
    expect(first.request.params.has('cursor')).toBe(false);
    expect(first.request.params.get('size')).toBe('10');
    first.flush({ items: range(1, 10), nextCursor: 'c1' });
    expect(store.status()).toBe('ready');
  });

  it('pede a próxima página ao faltar 3 reels e para quando acaba', () => {
    store.load();
    reels().flush({ items: range(1, 10), nextCursor: 'c1' });
    store.reached(5);
    http.expectNone((r) => r.url === '/api/reels');
    store.reached(7);
    const next = reels();
    expect(next.request.params.get('cursor')).toBe('c1');
    expect(store.loadingMore()).toBe(true);
    next.flush({ items: [card(10), ...range(11, 2)], nextCursor: null });
    expect(store.items().length).toBe(12); // o 10 repetido é ignorado
    expect(store.done()).toBe(true);
    store.reached(11);
    http.expectNone((r) => r.url === '/api/reels');
  });

  it('mostra erro só se não tiver nada na tela', () => {
    store.load();
    reels().flush(null, { status: 500, statusText: 'Erro' });
    expect(store.status()).toBe('error');
    store.load();
    reels().flush({ items: range(1, 4), nextCursor: 'c1' });
    store.reached(3);
    reels().flush(null, { status: 500, statusText: 'Erro' });
    expect(store.status()).toBe('ready');
    expect(store.loadingMore()).toBe(false);
  });
});
