import { ChangeDetectionStrategy, Component, ElementRef, input, signal, viewChild } from '@angular/core';
import { Icon } from '../../shared/ui/icon';

/**
 * Fileira com rolagem horizontal (touch e trackpad nativos, com scroll-snap).
 * Em telas com mouse aparecem setas para avançar/voltar uma "página".
 */
@Component({
  selector: 'ww-title-row',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section [attr.aria-labelledby]="headingId">
      <h2 [id]="headingId">{{ heading() }}</h2>
      <div class="viewport">
        <button type="button" class="arrow prev" aria-label="Voltar fileira" [disabled]="atStart()" (click)="page(-1)">
          <ww-icon name="chevron-left" />
        </button>
        <div #track class="track" (scroll)="update()">
          <ng-content />
        </div>
        <button type="button" class="arrow next" aria-label="Avançar fileira" [disabled]="atEnd()" (click)="page(1)">
          <ww-icon name="chevron-left" class="flip" />
        </button>
      </div>
    </section>
  `,
  styles: `
    section {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-3);
    }
    h2 {
      padding: 0 var(--ww-page-x);
      font-size: 20px;
      font-weight: 700;
      color: var(--ww-white);
    }
    .viewport {
      position: relative;
    }
    .track {
      display: flex;
      gap: var(--ww-space-2);
      overflow-x: auto;
      scroll-snap-type: x mandatory;
      scroll-padding: 0 var(--ww-page-x);
      padding: 4px var(--ww-page-x);
      scrollbar-width: none;
    }
    .track::-webkit-scrollbar {
      display: none;
    }
    .arrow {
      display: none;
    }
    @media (hover: hover) and (pointer: fine) {
      .arrow {
        position: absolute;
        top: 50%;
        z-index: 2;
        display: grid;
        place-items: center;
        width: var(--ww-touch);
        height: var(--ww-touch);
        border: var(--ww-hairline) solid var(--ww-border-dark, #3a3a3c);
        border-radius: 50%;
        background: var(--ww-surface-dark);
        color: var(--ww-white);
        cursor: pointer;
        transform: translateY(-50%);
        transition: opacity var(--ww-fast) var(--ww-ease);
      }
      .prev {
        left: 8px;
      }
      .next {
        right: 8px;
      }
      .arrow:disabled {
        opacity: 0;
        pointer-events: none;
      }
    }
    .flip {
      transform: scaleX(-1);
    }
  `,
})
export class TitleRow {
  private static next = 0;
  readonly heading = input.required<string>();
  protected readonly headingId = `row-${TitleRow.next++}`;

  private readonly track = viewChild.required<ElementRef<HTMLElement>>('track');
  protected readonly atStart = signal(true);
  protected readonly atEnd = signal(false);

  protected page(dir: 1 | -1): void {
    const el = this.track().nativeElement;
    el.scrollBy({ left: dir * el.clientWidth * 0.85, behavior: 'smooth' });
  }

  protected update(): void {
    const el = this.track().nativeElement;
    this.atStart.set(el.scrollLeft <= 2);
    this.atEnd.set(el.scrollLeft + el.clientWidth >= el.scrollWidth - 2);
  }

  ngAfterViewInit(): void {
    // depois que os cards entram, decide se a seta "avançar" faz sentido
    queueMicrotask(() => this.update());
  }
}
