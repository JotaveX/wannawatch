import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-config';
import { card } from '../../../testing/fixtures';
import { PAGE_SIZE, SwipeStore } from './swipe-store';

const range = (from: number, n: number) => Array.from({ length: n }, (_, i) => card(from + i));

describe('SwipeStore', () => {
  let store: SwipeStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SwipeStore, provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    store = TestBed.inject(SwipeStore);
    http = TestBed.inject(HttpTestingController);
  });

  const deck = () => http.expectOne((r) => r.url === '/api/swipe/deck');

  it('carrega a primeira página com seed e sem cursor', () => {
    store.start();
    expect(store.status()).toBe('loading');
    const req = deck();
    expect(req.request.params.get('seed')).toMatch(/^[a-z0-9]+$/);
    expect(req.request.params.has('cursor')).toBe(false);
    expect(req.request.params.get('size')).toBe(String(PAGE_SIZE));
    req.flush({ items: range(1, 3), nextCursor: null });
    expect(store.status()).toBe('ready');
    expect(store.current()?.id).toBe(1);
    expect(store.next()?.id).toBe(2);
  });

  it('registra a escolha, conta e volta o último', () => {
    store.start();
    deck().flush({ items: range(1, 3), nextCursor: null });
    store.decide('like');
    store.decide('pass');
    expect(store.current()?.id).toBe(3);
    expect(store.counts()).toEqual({ pass: 1, maybe: 0, like: 1, seen: 0 });
    expect(store.liked().map((c) => c.id)).toEqual([1]);
    expect(store.last()?.decision).toBe('pass');

    store.undo();
    expect(store.current()?.id).toBe(2);
    expect(store.counts().pass).toBe(0);
  });

  it('busca a próxima página com o cursor quando faltam poucas cartas, sem repetir a mesma seed', () => {
    store.start();
    const first = deck();
    const seed = first.request.params.get('seed');
    first.flush({ items: range(1, 6), nextCursor: 'c1' });
    store.decide('pass'); // sobram 5 → busca mais
    const second = deck();
    expect(second.request.params.get('cursor')).toBe('c1');
    expect(second.request.params.get('seed')).toBe(seed);
    second.flush({ items: [card(6), ...range(7, 3)], nextCursor: null }); // o 6 repetido é ignorado
    expect(store.cards().map((c) => c.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });

  it('avisa quando acabou e embaralha de novo com outra seed', () => {
    store.start();
    const first = deck();
    const seed = first.request.params.get('seed');
    first.flush({ items: range(1, 1), nextCursor: null });
    store.decide('seen');
    expect(store.finished()).toBe(true);

    store.reshuffle();
    const again = deck();
    expect(again.request.params.get('seed')).not.toBe(seed);
    again.flush({ items: range(1, 2), nextCursor: null });
    expect(store.history()).toEqual([]);
    expect(store.current()?.id).toBe(1);
  });

  it('mostra erro sem cartas e tenta de novo', () => {
    store.start();
    deck().flush(null, { status: 500, statusText: 'Erro' });
    expect(store.status()).toBe('error');
    store.retry();
    deck().flush({ items: range(1, 2), nextCursor: null });
    expect(store.status()).toBe('ready');
  });

  it('"quero ver" vai para o back e voltar desfaz', () => {
    store.start();
    deck().flush({ items: range(1, 3), nextCursor: null });
    store.decide('like');
    const put = http.expectOne('/api/likes/1');
    expect(put.request.method).toBe('PUT');
    put.flush(null);

    store.decide('pass');
    http.expectNone((r) => r.url.startsWith('/api/likes/2'));

    store.undo(); // desfaz o pass: nada no back
    store.undo(); // desfaz o like
    const del = http.expectOne('/api/likes/1');
    expect(del.request.method).toBe('DELETE');
  });
});
