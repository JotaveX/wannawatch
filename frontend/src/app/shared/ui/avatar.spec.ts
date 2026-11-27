import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UserRef } from '../../core/api/models';
import { Avatar } from './avatar';

@Component({
  imports: [Avatar],
  template: `<ww-avatar [user]="user()" [photo]="preview()" />`,
})
class Host {
  readonly user = signal<UserRef>({ id: 1, username: 'bia', displayName: 'Bia Souza', color: 2 });
  readonly preview = signal<string | null | undefined>(undefined);
}

describe('Avatar', () => {
  async function render(user: Partial<UserRef>, preview?: string | null) {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.user.update((u) => ({ ...u, ...user }));
    fixture.componentInstance.preview.set(preview);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('ww-avatar') as HTMLElement;
  }

  it('mostra a foto enviada, mesmo com um Bu escolhido', async () => {
    const el = await render({ mascot: 3, photoUrl: 'https://fotos.exemplo/bia.jpg' });
    expect(el.querySelector('img')!.getAttribute('src')).toBe('https://fotos.exemplo/bia.jpg');
    expect(el.style.background).toBe('transparent');
  });

  it('sem foto, cai para as iniciais na cor da pessoa', async () => {
    const el = await render({ photoUrl: null });
    expect(el.querySelector('img')).toBeNull();
    expect(el.textContent!.trim()).toBe('BS');
  });

  it('a prévia null esconde a foto salva (quem está trocando pelo Bu ou iniciais)', async () => {
    const el = await render({ photoUrl: 'https://fotos.exemplo/bia.jpg' }, null);
    expect(el.querySelector('img')).toBeNull();
  });
});
