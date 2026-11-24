import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { card } from '../../../testing/fixtures';
import { SwipePage } from './swipe-page';

const wait = (ms = 300) => new Promise((r) => setTimeout(r, ms));

describe('SwipePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/api' }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render() {
    const fixture = TestBed.createComponent(SwipePage);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/swipe/deck').flush({
      items: [card(1, { title: 'Primeiro' }), card(2, { title: 'Segundo' }), card(3, { title: 'Terceiro' })],
      nextCursor: null,
    });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const top = () => el.querySelector('ww-swipe-card.top h2')?.textContent;
    const status = () => el.querySelector('p.status')?.textContent?.trim();
    return { fixture, el, top, status };
  }

  it('empilha a carta atual por cima da próxima', async () => {
    const { el, top } = await render();
    expect(el.querySelectorAll('ww-swipe-card').length).toBe(2);
    expect(top()).toBe('Primeiro');
  });

  it('os botões decidem e o status anuncia a escolha', async () => {
    const { fixture, el, top, status } = await render();
    el.querySelector<HTMLButtonElement>('button[aria-label="Quero ver"]')!.click();
    await wait();
    await fixture.whenStable();
    expect(top()).toBe('Segundo');
    expect(status()).toBe('Você quer ver Primeiro');
  });

  it('atalhos de teclado: ← passa, ↑ talvez e Z volta', async () => {
    const { fixture, top, status } = await render();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    await wait();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
    await wait();
    await fixture.whenStable();
    expect(top()).toBe('Terceiro');
    expect(status()).toBe('Talvez: Segundo');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z' }));
    await fixture.whenStable();
    expect(top()).toBe('Segundo');
  });

  it('ignora atalhos com Ctrl e dentro de campos de texto', async () => {
    const { fixture, top } = await render();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', ctrlKey: true }));
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    input.remove();
    await wait();
    await fixture.whenStable();
    expect(top()).toBe('Primeiro');
  });
});
