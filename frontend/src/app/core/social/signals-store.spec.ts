import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../api/api-config';
import { SignalsStore } from './signals-store';

describe('SignalsStore', () => {
  let http: HttpTestingController;
  let store: SignalsStore;

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
    store = TestBed.inject(SignalsStore);
  });
  afterEach(() => localStorage.clear());

  const tick = () => new Promise<void>((r) => queueMicrotask(r));

  it('junta os pedidos do mesmo instante numa chamada e não pede de novo o que já conhece', async () => {
    store.ensure([1, 2]);
    store.ensure([2, 3]);
    await tick();
    const req = http.expectOne((r) => r.url === '/api/me/signals');
    expect(req.request.params.get('ids')).toBe('1,2,3');
    req.flush([
      { titleId: 1, kinds: ['SEEN', 'SAVED'] },
      { titleId: 2, kinds: [] },
    ]);
    expect(store.has(1, 'SEEN')).toBe(true);
    expect(store.has(1, 'SAVED')).toBe(true);
    expect(store.has(2, 'SEEN')).toBe(false);

    store.ensure([1, 2, 3]);
    await tick();
    http.expectNone((r) => r.url === '/api/me/signals');
  });

  it('marcar muda na hora e desfaz se o back recusar', () => {
    store.set(5, 'SEEN', true, 'DETAILS');
    expect(store.has(5, 'SEEN')).toBe(true);
    const req = http.expectOne('/api/titles/5/signals/SEEN?source=DETAILS');
    expect(req.request.method).toBe('PUT');
    req.flush(null, { status: 500, statusText: 'Erro' });
    expect(store.has(5, 'SEEN')).toBe(false);
  });

  it('desmarcar chama o DELETE e não mexe nos outros sinais do título', () => {
    store.set(5, 'SEEN', true);
    store.set(5, 'SAVED', true);
    http.match(() => true).forEach((r) => r.flush(null));
    store.toggle(5, 'SEEN');
    expect(http.expectOne('/api/titles/5/signals/SEEN').request.method).toBe('DELETE');
    expect(store.has(5, 'SEEN')).toBe(false);
    expect(store.has(5, 'SAVED')).toBe(true);
  });

  it('não chama o back se já está no estado pedido', () => {
    store.set(5, 'PASS', false);
    http.expectNone(() => true);
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
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SignalsStore).ensure([1]);
    await tick();
    http.expectNone(() => true);
  });
});
