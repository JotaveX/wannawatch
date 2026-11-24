import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App (shell)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
  });

  async function render(url: string) {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

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
    expect(el.querySelector('a.skip-link')?.getAttribute('href')).toBe('#conteudo');
  });
});
