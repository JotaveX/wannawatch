import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Segmented } from './segmented';

@Component({
  imports: [Segmented],
  template: `<ww-segmented label="Modo" [options]="opts" [(value)]="value" />`,
})
class Host {
  opts = [
    { value: 'group', label: 'Grupo' },
    { value: 'solo', label: 'Solo' },
  ];
  value = signal('group');
}

describe('Segmented', () => {
  it('marca a opção ativa com aria-pressed e atualiza o valor ao clicar', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const [grupo, solo] = Array.from(el.querySelectorAll('button'));

    expect(el.querySelector('ww-segmented')!.getAttribute('role')).toBe('group');
    expect(grupo.getAttribute('aria-pressed')).toBe('true');
    expect(solo.getAttribute('aria-pressed')).toBe('false');

    solo.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.value()).toBe('solo');
    expect(solo.getAttribute('aria-pressed')).toBe('true');
  });
});
