import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/api/api-config';
import { FriendsLiked } from '../../core/api/models';
import { card, homeResponse } from '../../../testing/fixtures';
import { HomePage } from './home-page';

describe('HomePage', () => {
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
  });

  async function render(response = homeResponse(), friends: FriendsLiked[] = []) {
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[aria-busy="true"]')).not.toBeNull();
    http.expectOne('/api/home').flush(response);
    http.expectOne((r) => r.url === '/api/likes/friends').flush(friends);
    await fixture.whenStable();
    return el;
  }

  it('mostra o destaque com trailer no YouTube e link para os detalhes', async () => {
    const el = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Noite em Marte');
    const trailer = el.querySelector<HTMLAnchorElement>('a[href^="https://www.youtube.com"]');
    expect(trailer?.target).toBe('_blank');
    expect(el.querySelector('a[href="/filme/1"]')?.textContent).toContain('Mais informações');
  });

  it('esconde o botão de trailer quando não há trailer', async () => {
    const base = homeResponse();
    const el = await render({ ...base, hero: { ...base.hero!, trailerUrl: null } });
    expect(el.textContent).not.toContain('Assistir trailer');
  });

  it('explica o destaque por quem curtiu', async () => {
    const base = homeResponse();
    const el = await render(base, [
      { title: card(1), likedByMe: false, friends: [{ id: 2, username: 'bia', displayName: 'Bia' }] },
    ]);
    expect(el.querySelector('.reason')?.textContent).toBe('Bia curtiu');
  });

  it('desenha as fileiras com títulos e o Top 10 numerado', async () => {
    const el = await render(homeResponse(), [
      { title: card(90), likedByMe: false, friends: [{ id: 2, username: 'bia', displayName: 'Bia' }] },
    ]);
    const headings = Array.from(el.querySelectorAll('h2')).map((h) => h.textContent?.trim());
    expect(headings).toEqual([
      'Top 10 de hoje no wannawatch?',
      'Seus amigos curtiram',
      'Porque você curtiu Noite em Marte',
      'Séries pra maratonar',
    ]);
    expect(el.querySelector('a[aria-label="1º lugar: Título 1"]')).not.toBeNull();
    expect(el.querySelector('a[href="/serie/40"]')).not.toBeNull();
  });

  it('mostra erro e tenta de novo', async () => {
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    http.expectOne('/api/home').flush(null, { status: 500, statusText: 'Erro' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Não deu pra carregar');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await fixture.whenStable();
    http.expectOne('/api/home').flush(homeResponse());
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('Noite em Marte');
  });
});
