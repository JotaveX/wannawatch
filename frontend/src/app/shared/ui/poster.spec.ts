import { TestBed } from '@angular/core/testing';
import { Poster } from './poster';

describe('Poster', () => {
  it('mostra o placeholder geométrico com o nome quando não há imagem', async () => {
    const fixture = TestBed.createComponent(Poster);
    fixture.componentRef.setInput('title', 'Noite em Marte');
    fixture.componentRef.setInput('seed', 3);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('img')).toBeNull();
    expect(el.querySelector('.circle')).not.toBeNull();
    expect(el.textContent).toContain('Noite em Marte');
  });

  it('cai no placeholder se a imagem falhar', async () => {
    const fixture = TestBed.createComponent(Poster);
    fixture.componentRef.setInput('image', { small: 'https://x/p.jpg', large: 'https://x/g.jpg' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const img = el.querySelector('img')!;
    expect(img.getAttribute('srcset')).toBe('https://x/p.jpg 1x, https://x/g.jpg 2x');
    expect(img.getAttribute('alt')).toBe('');

    img.dispatchEvent(new Event('error'));
    await fixture.whenStable();
    expect(el.querySelector('img')).toBeNull();
    expect(el.querySelector('.circle')).not.toBeNull();
  });
});
