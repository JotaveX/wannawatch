import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { LikesStore } from '../../core/social/likes-store';
import { reel } from '../../../testing/fixtures';
import { ReelsPage } from './reels-page';

describe('ReelsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // jsdom não tem scrollIntoView
    Element.prototype.scrollIntoView = vi.fn();
  });

  const VIDEO = { platform: 'YOUTUBE' as const, key: 'abcDEF12345' };

  async function render(
    items = [
      reel(1, { title: 'Primeiro', overview: 'Sinopse um.' }),
      reel(2, { title: 'Segundo', reason: { kind: 'PERSON', text: 'De Christopher Nolan' } }),
      reel(3, { title: 'Terceiro' }),
    ],
  ) {
    const fixture = TestBed.createComponent(ReelsPage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/reels').flush({ items, nextCursor: null });
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
    const { fixture, el } = await render();
    const [first, second] = Array.from(el.querySelectorAll('ww-reel-item'));
    expect(first.querySelectorAll('.sphere').length).toBe(2);
    // no reel, as esferas flutuam sem texto: o nome fica na esfera (e escondido para leitores de tela)
    expect(first.querySelector('ww-liked-by .label')).toBeNull();
    expect(first.querySelector('ww-liked-by .visually-hidden')?.textContent).toBe(
      'Bia e Caio curtiram',
    );
    const sphere = first.querySelector<HTMLButtonElement>('ww-liked-by button')!;
    expect(sphere.getAttribute('aria-label')).toBe('Bia curtiu');
    sphere.click();
    await fixture.whenStable();
    expect(first.querySelector('ww-liked-by .tip.open')?.textContent?.trim()).toBe('Bia');
    expect(
      first.querySelector('button[aria-label="Quero ver"]')?.getAttribute('aria-pressed'),
    ).toBe('true');
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
    const like = el.querySelector<HTMLButtonElement>(
      'ww-reel-item button[aria-label="Quero ver"]',
    )!;
    like.click();
    http
      .expectOne((r) => r.url === '/api/likes/1')
      .flush(null, { status: 500, statusText: 'Erro' });
    await fixture.whenStable();
    expect(like.getAttribute('aria-pressed')).toBe('false');
  });

  it('quero ver e salvar alternam pelo botão e pelo teclado', async () => {
    const { fixture, el } = await render();
    const like = el.querySelector<HTMLButtonElement>(
      'ww-reel-item button[aria-label="Quero ver"]',
    )!;
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
    expect(
      el
        .querySelector('ww-reel-item button[aria-label="Salvar na lista"]')
        ?.getAttribute('aria-pressed'),
    ).toBe('true');
    expect(http.expectOne('/api/titles/1/signals/SAVED?source=REELS').request.method).toBe('PUT');
  });

  it('com login, traz o que já estava salvo', async () => {
    localStorage.setItem(
      'ww.session',
      JSON.stringify({ token: 't', user: { id: 1, username: 'jotave', displayName: 'Jotave' } }),
    );
    const { fixture, el } = await render();
    await new Promise<void>((r) => queueMicrotask(r));
    http.expectOne((r) => r.url === '/api/likes').flush([]);
    const req = http.expectOne((r) => r.url === '/api/me/signals');
    expect(req.request.params.get('ids')).toBe('1,2,3');
    req.flush([{ titleId: 1, kinds: ['SAVED'] }]);
    await fixture.whenStable();
    expect(
      el
        .querySelector('ww-reel-item button[aria-label="Salvar na lista"]')
        ?.getAttribute('aria-pressed'),
    ).toBe('true');
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

  describe('com vídeo', () => {
    const withVideo = () => [
      reel(1, { title: 'Primeiro', overview: 'Sinopse um.', video: VIDEO }),
      reel(2, { title: 'Segundo', video: VIDEO }),
      reel(3, { title: 'Terceiro' }),
    ];

    it('só o reel ativo monta o player, sem som e sem os controles do YouTube', async () => {
      const { fixture, el } = await render(withVideo());
      const iframes = el.querySelectorAll('iframe');
      expect(iframes.length).toBe(1);
      const src = iframes[0].getAttribute('src')!;
      expect(src).toContain('https://www.youtube-nocookie.com/embed/abcDEF12345?');
      expect(src).toContain('mute=1');
      expect(src).toContain('controls=0');
      expect(iframes[0].getAttribute('title')).toBe('Vídeo de Primeiro');
      key('j');
      await fixture.whenStable();
      expect(el.querySelectorAll('iframe').length).toBe(1);
      expect(el.querySelectorAll('ww-reel-item')[1].querySelector('iframe')).not.toBeNull();
      key('j'); // o terceiro não tem vídeo
      await fixture.whenStable();
      expect(el.querySelectorAll('iframe').length).toBe(0);
      expect(el.querySelectorAll('ww-reel-item')[2].querySelector('.synopsis')?.textContent).toBe(
        'Sinopse.',
      );
    });

    it('só o título fica por cima do vídeo, e o som vale para o feed todo', async () => {
      const { fixture, el } = await render(withVideo());
      const first = el.querySelector('ww-reel-item')!;
      expect(first.querySelector('.synopsis')).toBeNull();
      expect(first.querySelector('h2')?.textContent).toBe('Primeiro');
      const texts = Array.from(first.querySelectorAll('button, a')).map((b) => b.textContent ?? '');
      expect(texts.some((t) => /Ver sinopse|Ver detalhes/.test(t))).toBe(false);

      const sound = first.querySelector<HTMLButtonElement>('button[aria-label="Som do vídeo"]')!;
      expect(sound.getAttribute('aria-pressed')).toBe('false');
      key('m');
      await fixture.whenStable();
      expect(sound.getAttribute('aria-pressed')).toBe('true');
      const second = el.querySelectorAll('ww-reel-item')[1];
      expect(
        second.querySelector('button[aria-label="Som do vídeo"]')?.getAttribute('aria-pressed'),
      ).toBe('true');
    });

    it('o vídeo ocupa o reel sem o pôster atrás e os botões de ação ficam só no ícone', async () => {
      const { el } = await render(withVideo());
      const [first, , third] = Array.from(el.querySelectorAll('ww-reel-item'));
      expect(first.querySelector('ww-poster')).toBeNull();
      expect(third.querySelector('ww-poster')).not.toBeNull();
      // sem ninguém querendo ver, não sobra legenda nenhuma embaixo dos botões
      expect(first.querySelectorAll('.actions span').length).toBe(0);
      // o iframe só aparece quando o player avisa que o vídeo está na tela
      expect(first.querySelector('iframe')?.classList.contains('on')).toBe(false);
    });

    it('o vídeo só aparece tocando, o botão do meio cobre o ícone do YouTube e o loop é nosso', async () => {
      const { fixture, el } = await render(withVideo());
      const iframe = el.querySelector('iframe')!;
      const reel = el.querySelector('ww-reel-item')!;
      const center = () => reel.querySelector('.round.big');
      const win = iframe.contentWindow!;
      const post = vi.spyOn(win, 'postMessage').mockImplementation(() => {});
      const player = async (info: object) => {
        window.dispatchEvent(
          new MessageEvent('message', {
            data: JSON.stringify({ event: 'infoDelivery', info }),
            origin: 'https://www.youtube-nocookie.com',
            source: win,
          }),
        );
        await fixture.whenStable();
      };
      const sent = () => post.mock.calls.map(([m]) => JSON.parse(String(m)));
      expect(iframe.src).not.toContain('loop=1');
      await player({ playerState: 3, currentTime: 0, duration: 20 });
      expect(iframe.classList.contains('on')).toBe(false); // carregando: o YouTube mostraria a capa
      expect(center()).toBeFalsy();
      await player({ playerState: 1, currentTime: 0.1 });
      expect(iframe.classList.contains('on')).toBe(false);
      expect(center()).toBeTruthy(); // começou a tocar: o YouTube desenha o ícone no meio
      await player({ currentTime: 0.5 });
      expect(iframe.classList.contains('on')).toBe(true);
      // o autoplay já toca: nenhum playVideo a mais
      expect(sent().map((c) => c.func)).not.toContain('playVideo');

      // pausado, o quadro parado (limpo) continua na tela
      key('p');
      await player({ playerState: 2 });
      expect(iframe.classList.contains('on')).toBe(true);
      expect(reel.querySelector('.shade')).toBeNull();
      key('p');
      await player({ playerState: 1 });
      expect(sent().filter((c) => c.func === 'playVideo').length).toBe(1);
      expect(iframe.classList.contains('on')).toBe(true);
      expect(center()).toBeTruthy();

      await player({ currentTime: 19.7 });
      expect(sent()).toContainEqual({ event: 'command', func: 'seekTo', args: [0, true] });
      expect(iframe.classList.contains('on')).toBe(true);
      await player({ playerState: 0 });
      expect(iframe.classList.contains('on')).toBe(false);
    });

    it('o volume liga o som, vai ao zero e vale para o feed todo', async () => {
      const { fixture, el } = await render(withVideo());
      const [first, second] = Array.from(el.querySelectorAll('ww-reel-item'));
      const slider = first.querySelector<HTMLInputElement>('input[aria-label="Volume"]')!;
      const sound = first.querySelector<HTMLButtonElement>('button[aria-label="Som do vídeo"]')!;
      expect(slider.value).toBe('0'); // começa sem som
      slider.value = '40';
      slider.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      expect(sound.getAttribute('aria-pressed')).toBe('true');
      expect(slider.getAttribute('aria-valuetext')).toBe('40%');
      expect(second.querySelector<HTMLInputElement>('input[aria-label="Volume"]')!.value).toBe(
        '40',
      );
      slider.value = '0';
      slider.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      expect(sound.getAttribute('aria-pressed')).toBe('false');
      sound.click(); // ligar o som do zero volta para a metade
      await fixture.whenStable();
      expect(slider.value).toBe('50');
    });

    it('P e o botão pausam o vídeo', async () => {
      const { fixture, el } = await render(withVideo());
      const pause = el.querySelector<HTMLButtonElement>(
        'ww-reel-item button[aria-label="Pausar vídeo"]',
      )!;
      expect(pause.getAttribute('aria-pressed')).toBe('false');
      key('p');
      await fixture.whenStable();
      expect(pause.getAttribute('aria-pressed')).toBe('true');
      pause.click();
      await fixture.whenStable();
      expect(pause.getAttribute('aria-pressed')).toBe('false');
    });
  });
});
