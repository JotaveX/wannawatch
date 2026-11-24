import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { SocialMock } from '../../mocks/social.mock';
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
      items: [card(1, { title: 'Primeiro', overview: 'Sinopse um.' }), card(2, { title: 'Segundo' }), card(3, { title: 'Terceiro' })],
      nextCursor: null,
    });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const key = (k: string) => document.dispatchEvent(new KeyboardEvent('keydown', { key: k }));

  it('mostra a sinopse em destaque com título, match e link de detalhes', async () => {
    const { el } = await render();
    const first = el.querySelector('ww-reel-item')!;
    expect(first.querySelector('.synopsis')?.textContent).toBe('Sinopse um.');
    expect(first.querySelector('h2')?.textContent).toBe('Primeiro');
    expect(first.textContent).toContain('% match');
    expect(first.querySelector('a[href="/filme/1"]')).not.toBeNull();
  });

  it('mostra as esferas dos amigos que curtiram só quando há amigos', async () => {
    const social = TestBed.inject(SocialMock);
    const { el } = await render();
    const items = Array.from(el.querySelectorAll('ww-reel-item'));
    items.forEach((item, i) => {
      const friends = social.friendsWhoLiked(i + 1);
      expect(item.querySelectorAll('.sphere').length).toBe(friends.length);
      if (friends.length) expect(item.querySelector('ww-liked-by .label')?.textContent).toContain('também curti');
    });
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

  it('quero ver e salvar alternam pelo botão e pelo teclado', async () => {
    const { fixture, el } = await render();
    const like = el.querySelector<HTMLButtonElement>('ww-reel-item button[aria-label="Quero ver"]')!;
    like.click();
    await fixture.whenStable();
    expect(like.getAttribute('aria-pressed')).toBe('true');
    key('l');
    await fixture.whenStable();
    expect(like.getAttribute('aria-pressed')).toBe('false');
    key('s');
    await fixture.whenStable();
    expect(el.querySelector('ww-reel-item button[aria-label="Salvar na lista"]')?.getAttribute('aria-pressed')).toBe('true');
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
