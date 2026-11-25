import { signal } from '@angular/core';

export interface SliderBox {
  x: number;
  y: number;
  w: number;
  h: number;
}

/**
 * Posição do indicador que desliza até o link ativo de um menu.
 *
 * Cada link avisa pelo `(isActiveChange)` do routerLinkActive:
 *   <a #a routerLinkActive="active" (isActiveChange)="slider.track(a, $event)">
 * O indicador fica num elemento absoluto dentro do menu (que precisa de position: relative)
 * e usa `box()` para se posicionar. A primeira posição entra sem animação, para não
 * deslizar a partir do canto quando a tela abre.
 */
export class ActiveSlider {
  readonly box = signal<SliderBox | null>(null);
  /** false até a primeira posição ser desenhada */
  readonly animated = signal(false);
  private active: HTMLElement | null = null;

  track(el: HTMLElement, isActive: boolean): void {
    if (isActive) {
      this.active = el;
      this.measure();
    } else if (this.active === el) {
      this.active = null;
      this.box.set(null);
    }
  }

  /** Remede (ex.: depois de um resize que esconde ou mostra links). */
  measure(): void {
    const el = this.active;
    if (!el || !el.offsetWidth) {
      this.box.set(null);
      return;
    }
    this.box.set({ x: el.offsetLeft, y: el.offsetTop, w: el.offsetWidth, h: el.offsetHeight });
    if (!this.animated() && typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(() => requestAnimationFrame(() => this.animated.set(true)));
    }
  }
}
