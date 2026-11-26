import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api/api-config';
import { likedByLabel, LikesStore } from './likes-store';

const bia = { id: 2, username: 'bia', displayName: 'Bia Rocha' };
const caio = { id: 3, username: 'caio', displayName: 'Caio' };
const duda = { id: 4, username: 'duda', displayName: 'Duda' };

describe('LikesStore', () => {
  let http: HttpTestingController;
  let store: LikesStore;

  beforeEach(() => {
    localStorage.setItem(
      'ww.session',
      JSON.stringify({ token: 't', user: { id: 1, username: 'jotave', displayName: 'Jotave' } }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(LikesStore);
  });
  afterEach(() => localStorage.clear());

  const tick = () => new Promise<void>((r) => queueMicrotask(r));

  it('junta os pedidos do mesmo instante numa chamada e não pede de novo o que já conhece', async () => {
    store.ensure([1, 2]);
    store.ensure([2, 3]);
    await tick();
    const req = http.expectOne((r) => r.url === '/api/likes');
    expect(req.request.params.get('titleIds')).toBe('1,2,3');
    req.flush([{ titleId: 1, likedByMe: true, friends: [bia] }]);
    expect(store.likedByMe(1)).toBe(true);
    expect(store.friends(1)).toEqual([bia]);

    store.ensure([1, 2, 3]);
    await tick();
    http.expectNone((r) => r.url === '/api/likes');
  });

  it('curtir muda na hora e desfaz se o back recusar', () => {
    store.setLiked(5, true);
    expect(store.likedByMe(5)).toBe(true);
    http.expectOne('/api/likes/5').flush(null, { status: 500, statusText: 'Erro' });
    expect(store.likedByMe(5)).toBe(false);
  });

  it('não chama o back se já está no estado pedido', () => {
    store.remember([{ titleId: 5, likedByMe: true, friends: [] }]);
    store.setLiked(5, true);
    http.expectNone('/api/likes/5');
  });

  it('sem login não busca nada', async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    TestBed.inject(LikesStore).ensure([1]);
    await tick();
    TestBed.inject(HttpTestingController).expectNone((r) => r.url === '/api/likes');
  });
});

describe('likedByLabel', () => {
  it('fala o primeiro nome e resume quando são muitos', () => {
    expect(likedByLabel([])).toBeNull();
    expect(likedByLabel([bia])).toBe('Bia curtiu');
    expect(likedByLabel([bia, caio])).toBe('Bia e Caio curtiram');
    expect(likedByLabel([bia, caio, duda])).toBe('Bia e mais 2 curtiram');
  });
});
