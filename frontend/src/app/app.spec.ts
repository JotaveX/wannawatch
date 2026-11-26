import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

const session = { token: 't', user: { id: 1, username: 'jotave', displayName: 'Jotave' } };

describe('App (shell)', () => {
  beforeEach(() => {
    localStorage.setItem('ww.session', JSON.stringify(session));
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => localStorage.clear());

  async function render(url: string) {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('sem login, qualquer tela vai para /entrar, sem navegação, guardando a volta', async () => {
    localStorage.clear();
    const el = await render('/match/ABC123');
    expect(TestBed.inject(Router).url).toBe('/entrar?volta=%2Fmatch%2FABC123');
    expect(el.querySelector('ww-tab-bar')).toBeNull();
    expect(el.querySelector('h1')?.textContent).toBe('Que bom te ver de novo!');
  });

  it('logado, /entrar volta para a Home', async () => {
    await render('/entrar');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('a aba Match substitui Grupos', async () => {
    const el = await render('/swipe');
    const tabs = Array.from(el.querySelectorAll('ww-tab-bar a')).map((a) => a.textContent?.trim());
    expect(tabs).toEqual(['Início', 'Swipe', 'Reels', 'Match', 'Perfil']);
  });

  it('Swipe usa a barra lateral e o tema claro', async () => {
    const el = await render('/swipe');
    expect(el.querySelector('ww-side-nav')).not.toBeNull();
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('Home não usa a barra lateral (tem menu próprio) e é escura', async () => {
    const el = await render('/');
    expect(el.querySelector('ww-side-nav')).toBeNull();
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('a barra de abas está sempre presente e marca a página atual', async () => {
    const el = await render('/reels');
    const current = el.querySelector('ww-tab-bar a[aria-current="page"]');
    expect(current?.textContent).toContain('Reels');
  });

  it('tem link para pular direto ao conteúdo', async () => {
    const el = await render('/');
    const skip = el.querySelector<HTMLAnchorElement>('a.skip-link')!;
    skip.click();
    expect(document.activeElement?.id).toBe('conteudo');
  });
});
