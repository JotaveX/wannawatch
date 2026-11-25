import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-config';
import { Room } from '../../core/api/models';
import { card } from '../../../testing/fixtures';
import { MatchRoomStore, POLL_MS } from './match-room-store';

const me = { id: 1, username: 'jotave', displayName: 'Jotave' };
const bia = { id: 2, username: 'bia', displayName: 'Bia' };
const caio = { id: 3, username: 'caio', displayName: 'Caio' };

function room(over: Partial<Room> = {}): Room {
  return {
    code: 'K7M2QX',
    mediaType: 'MOVIE',
    genre: null,
    owner: me,
    participants: [
      { user: me, votes: 0, likes: 0, joinedAt: '2026-11-24T20:00:00Z' },
      { user: bia, votes: 0, likes: 0, joinedAt: '2026-11-24T20:01:00Z' },
    ],
    status: 'VOTING',
    mascot: 3,
    winner: null,
    startedAt: '2026-11-24T20:02:00Z',
    matchedAt: null,
    createdAt: '2026-11-24T20:00:00Z',
    ...over,
  };
}

describe('MatchRoomStore', () => {
  let http: HttpTestingController;
  let store: MatchRoomStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MatchRoomStore, provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(MatchRoomStore);
  });
  afterEach(() => vi.useRealTimers());

  function open(r = room()) {
    store.start(' k7m2qx ');
    http.expectOne('/api/match/rooms/K7M2QX/join').flush(r);
    if (r.status === 'VOTING') {
      http.expectOne((req) => req.url === '/api/match/rooms/K7M2QX/deck').flush({
        items: [card(1), card(2), card(3)],
        nextCursor: null,
      });
    }
  }

  it('entra na sala (código em maiúsculas) e carrega o deck', () => {
    open();
    expect(store.status()).toBe('ready');
    expect(store.participants().length).toBe(2);
    expect(store.alone()).toBe(false);
    expect(store.current()?.id).toBe(1);
  });

  it('sala inexistente vira "não encontrada"', () => {
    store.start('NADA00');
    http.expectOne('/api/match/rooms/NADA00/join').flush(null, { status: 404, statusText: 'Not Found' });
    expect(store.status()).toBe('notfound');
  });

  it('vota, avança e mostra o vencedor quando o back diz que deu match', () => {
    open();
    store.vote(false);
    const pass = http.expectOne('/api/match/rooms/K7M2QX/votes/1');
    expect(pass.request.method).toBe('PUT');
    expect(pass.request.body).toEqual({ liked: false });
    pass.flush(room());

    store.vote(true);
    http.expectOne('/api/match/rooms/K7M2QX/votes/2').flush(room({ status: 'MATCHED', winner: card(2) }));
    expect(store.matched()).toBe(true);
    expect(store.winner()?.id).toBe(2);
    expect(store.freshMatch()).toBe(true);

    store.vote(true); // depois do match não vota mais
    http.expectNone('/api/match/rooms/K7M2QX/votes/3');
  });

  it('voltar desfaz o último voto no back', () => {
    open();
    store.vote(true);
    http.expectOne('/api/match/rooms/K7M2QX/votes/1').flush(room());
    store.undo();
    expect(store.current()?.id).toBe(1);
    expect(http.expectOne('/api/match/rooms/K7M2QX/votes/1').request.method).toBe('DELETE');
  });

  it('se o voto falhar, devolve a carta e avisa', () => {
    open();
    store.vote(true);
    http.expectOne('/api/match/rooms/K7M2QX/votes/1').flush(null, { status: 500, statusText: 'Erro' });
    expect(store.current()?.id).toBe(1);
    expect(store.problem()).toContain('Não deu pra registrar');
  });

  it('consulta a sala de tempos em tempos e para quando dá match', () => {
    vi.useFakeTimers();
    open();
    vi.advanceTimersByTime(POLL_MS);
    http.expectOne('/api/match/rooms/K7M2QX').flush(room());
    vi.advanceTimersByTime(POLL_MS);
    http.expectOne('/api/match/rooms/K7M2QX').flush(room({ status: 'MATCHED', winner: card(3) }));
    expect(store.winner()?.id).toBe(3);
    vi.advanceTimersByTime(POLL_MS * 3);
    http.expectNone('/api/match/rooms/K7M2QX');
  });

  it('uma resposta atrasada não desfaz o match', () => {
    open();
    store.vote(true);
    store.vote(true);
    const [first, second] = [
      http.expectOne('/api/match/rooms/K7M2QX/votes/1'),
      http.expectOne('/api/match/rooms/K7M2QX/votes/2'),
    ];
    second.flush(room({ status: 'MATCHED', winner: card(2) }));
    first.flush(room());
    expect(store.matched()).toBe(true);
  });

  it('sala já decidida não carrega deck', () => {
    open(room({ status: 'MATCHED', winner: card(9) }));
    http.expectNone((req) => req.url.endsWith('/deck'));
    expect(store.winner()?.id).toBe(9);
    expect(store.freshMatch()).toBe(false); // sem festa para quem chega depois
  });

  describe('sala de espera', () => {
    const waiting = (over: Partial<Room> = {}) => room({ status: 'WAITING', startedAt: null, ...over });
    const alone = { participants: [{ user: me, votes: 0, likes: 0, joinedAt: '2026-11-24T20:00:00Z' }] };

    it('esperando não carrega o deck', () => {
      open(waiting(alone));
      expect(store.waiting()).toBe(true);
      http.expectNone((req) => req.url.endsWith('/deck'));
    });

    it('avisa quem entrou e quem saiu', () => {
      vi.useFakeTimers();
      open(waiting(alone));
      vi.advanceTimersByTime(POLL_MS);
      http.expectOne('/api/match/rooms/K7M2QX').flush(waiting());
      expect(store.notice()?.text).toBe('Bia entrou na sala');
      const first = store.notice()!.key;

      vi.advanceTimersByTime(POLL_MS);
      http.expectOne('/api/match/rooms/K7M2QX').flush(
        waiting({
          participants: [...waiting().participants, { user: caio, votes: 0, likes: 0, joinedAt: '2026-11-24T20:03:00Z' }],
        }),
      );
      expect(store.notice()?.text).toBe('Caio entrou na sala');
      expect(store.notice()!.key).not.toBe(first);

      vi.advanceTimersByTime(POLL_MS);
      http.expectOne('/api/match/rooms/K7M2QX').flush(waiting(alone));
      expect(store.notice()?.text).toBe('Bia e Caio saíram da sala');
    });

    it('quando o dono começa, todo mundo carrega o deck', () => {
      vi.useFakeTimers();
      open(waiting());
      vi.advanceTimersByTime(POLL_MS);
      http.expectOne('/api/match/rooms/K7M2QX').flush(room());
      expect(store.waiting()).toBe(false);
      http.expectOne((req) => req.url === '/api/match/rooms/K7M2QX/deck').flush({ items: [card(1)], nextCursor: null });
      expect(store.current()?.id).toBe(1);
    });

    it('o dono começa pelo /start', () => {
      open(waiting());
      store.startVoting();
      const req = http.expectOne('/api/match/rooms/K7M2QX/start');
      expect(req.request.method).toBe('POST');
      req.flush(room());
      expect(store.starting()).toBe(false);
      http.expectOne((r) => r.url === '/api/match/rooms/K7M2QX/deck').flush({ items: [card(1)], nextCursor: null });
      expect(store.current()?.id).toBe(1);
    });

    it('começar sozinho mostra o motivo', () => {
      open(waiting(alone));
      store.startVoting();
      http.expectOne('/api/match/rooms/K7M2QX/start').flush(null, { status: 409, statusText: 'Conflict' });
      expect(store.startError()).toContain('pelo menos mais uma pessoa');
      expect(store.waiting()).toBe(true);
    });

    it('uma consulta atrasada não volta a sala para a espera', () => {
      vi.useFakeTimers();
      open(waiting());
      store.startVoting();
      http.expectOne('/api/match/rooms/K7M2QX/start').flush(room());
      http.expectOne((r) => r.url === '/api/match/rooms/K7M2QX/deck').flush({ items: [card(1)], nextCursor: null });
      vi.advanceTimersByTime(POLL_MS);
      http.expectOne('/api/match/rooms/K7M2QX').flush(waiting()); // saiu antes do /start
      expect(store.waiting()).toBe(false);
      expect(store.current()?.id).toBe(1);
    });

    it('sala fechada pelo dono vira "fechada" e para de consultar', () => {
      vi.useFakeTimers();
      open(waiting());
      vi.advanceTimersByTime(POLL_MS);
      http.expectOne('/api/match/rooms/K7M2QX').flush(null, { status: 404, statusText: 'Not Found' });
      expect(store.status()).toBe('closed');
      vi.advanceTimersByTime(POLL_MS * 3);
      http.expectNone('/api/match/rooms/K7M2QX');
    });

    it('sala que já começou sem mim vira "trancada"', () => {
      store.start('K7M2QX');
      http.expectOne('/api/match/rooms/K7M2QX/join').flush(null, { status: 409, statusText: 'Conflict' });
      expect(store.status()).toBe('locked');
    });
  });
});
