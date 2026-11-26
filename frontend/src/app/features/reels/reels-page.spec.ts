import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { LikesStore } from '../../core/social/likes-store';
import { card } from '../../../testing/fixtures';
import { ReelsPage } from './reels-page';

describe('ReelsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    http = TestBed.inject(HttpTestingController);
    // jsdom não tem scrollIntoView
    Element.prototype.scrollIntoView = vi.fn();
  });

  async function render() {
    const fixture = TestBed.createComponent(ReelsPage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/reels').flush({
      items: [card(1, { title: 'Primeiro', overview: 'Sinopse um.' }), card(2, { title: 'Segundo', reason: { kind: 'PERSON', text: 'De Christopher Nolan' } }), card(3, { title: 'Terceiro' })],
      nextCursor: null,
    });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const key = (k: string) => document.dispatchEvent(new KeyboardEvent('keydown', { key: k }));

  it('mostra a sinopse em destaque com título e link de detalhes', async () => {
    const { el } = await render();
    const first = el.querySelector('ww-reel-item')!;
    expect(first.querySelector('.synopsis')?.textContent).toBe('Sinopse um.');
    expect(first.querySelector('h2')?.textContent).toBe('Primeiro');
    expect(first.querySelector('a[href="/filme/1"]')).not.toBeNull();
  });

  it('mostra as esferas dos amigos que curtiram só quando há amigos, e conta quem quer ver', async () => {
    TestBed.inject(LikesStore).remember([
      {
        titleId: 1,
        likedByMe: true,
        friends: [
          { id: 2, username: 'bia', displayName: 'Bia' },
          { id: 3, username: 'caio', displayName: 'Caio' },
        ],
      },
    ]);
    const { el } = await render();
    const [first, second] = Array.from(el.querySelectorAll('ww-reel-item'));
    expect(first.querySelectorAll('.sphere').length).toBe(2);
    expect(first.querySelector('ww-liked-by .label')?.textContent).toBe('Bia e Caio curtiram');
    expect(first.querySelector('button[aria-label="Quero ver"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(first.querySelector('.actions span')?.textContent).toBe('3');
    expect(second.querySelectorAll('.sphere').length).toBe(0);
    // sem amigos, o motivo da recomendação ocupa o lugar
    expect(second.querySelector('ww-liked-by .label')?.textContent).toBe('De Christopher Nolan');
    const described = second.querySelector('article')!.getAttribute('aria-describedby')!;
    expect(second.querySelector('#' + described)?.textContent).toContain('De Christopher Nolan');
  });

  it('J/K e setas navegam e o painel acompanha', async () => {
    const { fixture, el } = await render();
    key('j');
    await fixture.whenStable();
    expect(el.querySelector('.panel h3')?.textContent).toBe('Segundo');
    key('ArrowDown');
    key('ArrowDown'); // já está no último: não passa
    await fixture.whenStable();
    expect(el.querySelector('.panel h3')?.textContent).toBe('Terceiro');
    key('k');
    await fixture.whenStable();
    expect(el.querySelector('.panel h3')?.textContent).toBe('Segundo');
  });

  it('desfaz o quero ver se o back recusar', async () => {
    const { fixture, el } = await render();
    const like = el.querySelector<HTMLButtonElement>('ww-reel-item button[aria-label="Quero ver"]')!;
    like.click();
    http.expectOne((r) => r.url === '/api/likes/1').flush(null, { status: 500, statusText: 'Erro' });
    await fixture.whenStable();
    expect(like.getAttribute('aria-pressed')).toBe('false');
  });

  it('quero ver e salvar alternam pelo botão e pelo teclado', async () => {
    const { fixture, el } = await render();
    const like = el.querySelector<HTMLButtonElement>('ww-reel-item button[aria-label="Quero ver"]')!;
    like.click();
    await fixture.whenStable();
    expect(like.getAttribute('aria-pressed')).toBe('true');
    const put = http.expectOne('/api/likes/1?source=REELS');
    expect(put.request.method).toBe('PUT');
    put.flush(null);
    key('l');
    await fixture.whenStable();
    expect(like.getAttribute('aria-pressed')).toBe('false');
    expect(http.expectOne('/api/likes/1?source=REELS').request.method).toBe('DELETE');
    key('s');
    await fixture.whenStable();
    expect(el.querySelector('ww-reel-item button[aria-label="Salvar na lista"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(http.expectOne('/api/titles/1/signals/SAVED?source=REELS').request.method).toBe('PUT');
  });

  it('com login, traz o que já estava salvo', async () => {
    localStorage.setItem('ww.session', JSON.stringify({ token: 't', user: { id: 1, username: 'jotave', displayName: 'Jotave' } }));
    const { fixture, el } = await render();
    await new Promise<void>((r) => queueMicrotask(r));
    http.expectOne((r) => r.url === '/api/likes').flush([]);
    const req = http.expectOne((r) => r.url === '/api/me/signals');
    expect(req.request.params.get('ids')).toBe('1,2,3');
    req.flush([{ titleId: 1, kinds: ['SAVED'] }]);
    await fixture.whenStable();
    expect(el.querySelector('ww-reel-item button[aria-label="Salvar na lista"]')?.getAttribute('aria-pressed')).toBe('true');
    localStorage.clear();
  });

  it('compartilhar copia o link quando não há compartilhamento nativo', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('ww-reel-item button[aria-label="Compartilhar"]')!.click();
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();
    expect(writeText).toHaveBeenCalledWith(expect.stringMatching(/\/filme\/1$/));
    expect(el.querySelector('.toast')?.textContent).toBe('Link copiado');
  });
});
