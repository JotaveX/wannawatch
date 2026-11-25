import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LikesStore } from '../../core/social/likes-store';
import { card } from '../../../testing/fixtures';
import { dragDecision, SwipeCard, THRESHOLD_X, THRESHOLD_Y } from './swipe-card';

describe('dragDecision', () => {
  it('decide pela direção dominante e só depois do limite', () => {
    expect(dragDecision(THRESHOLD_X, 10)).toBe('like');
    expect(dragDecision(-THRESHOLD_X, 10)).toBe('pass');
    expect(dragDecision(10, -THRESHOLD_Y)).toBe('maybe');
    expect(dragDecision(10, THRESHOLD_Y)).toBe('seen');
    expect(dragDecision(THRESHOLD_X - 1, 0)).toBeNull();
    expect(dragDecision(60, -80)).toBeNull();
  });

  it('sem vertical (Match), só vale para os lados', () => {
    expect(dragDecision(10, -THRESHOLD_Y * 2, false)).toBeNull();
    expect(dragDecision(THRESHOLD_X, -THRESHOLD_Y * 2, false)).toBe('like');
  });
});

describe('SwipeCard', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }),
  );

  async function render(interactive = true) {
    const fixture = TestBed.createComponent(SwipeCard);
    fixture.componentRef.setInput('card', card(7, { title: 'Noite em Marte' }));
    fixture.componentRef.setInput('interactive', interactive);
    await fixture.whenStable();
    return fixture;
  }

  it('mostra título, quem curtiu e link de informações acessível', async () => {
    TestBed.inject(LikesStore).remember([
      { titleId: 7, likedByMe: false, friends: [{ id: 2, username: 'bia', displayName: 'Bia' }] },
    ]);
    const el = (await render()).nativeElement as HTMLElement;
    expect(el.querySelector('h2')?.textContent).toBe('Noite em Marte');
    expect(el.querySelector('.badge')?.textContent?.trim()).toBe('Bia curtiu');
    expect(el.querySelector('a[aria-label="Mais informações sobre Noite em Marte"]')?.getAttribute('href')).toBe('/filme/7');
  });

  it('sem curtidas de amigos, sem selo', async () => {
    const el = (await render()).nativeElement as HTMLElement;
    expect(el.querySelector('.badge')).toBeNull();
  });

  it('a carta de trás fica escondida dos leitores de tela e sem link', async () => {
    const el = (await render(false)).nativeElement as HTMLElement;
    expect(el.querySelector('article')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('a')).toBeNull();
  });

  it('fling avisa a decisão uma vez só', async () => {
    const fixture = await render();
    const seen: string[] = [];
    fixture.componentInstance.decided.subscribe((d) => seen.push(d));
    fixture.componentInstance.fling('like');
    fixture.componentInstance.fling('pass');
    await new Promise((r) => setTimeout(r, 300));
    expect(seen).toEqual(['like']);
  });
});
