import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '../../shared/ui/button';
import { Icon, IconName } from '../../shared/ui/icon';
import { Mascot } from '../../shared/ui/mascot';

/**
 * Tela provisória. Usada para o que ainda não existe, para a página de "não encontrado" e para
 * as telas das próximas fases. Texto vem do `data` da rota.
 */
@Component({
  selector: 'ww-placeholder-page',
  imports: [RouterLink, Button, Icon, Mascot],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ww-stagger">
      @if (mascot()) {
        <ww-mascot class="mascot" [size]="120" />
      } @else {
        <span class="mark"><ww-icon [name]="icon()" [size]="30" /></span>
      }
      <h1>{{ heading() }}</h1>
      <p>{{ message() }}</p>
      <a wwButton variant="soft" routerLink="/swipe">Bora dar uns swipes</a>
    </section>
  `,
  styles: `
    :host {
      display: grid;
      place-items: center;
      min-height: 70dvh;
      padding: var(--ww-space-8) var(--ww-page-x);
    }
    section {
      max-width: 420px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--ww-space-3);
      text-align: center;
    }
    .mark {
      display: grid;
      place-items: center;
      width: 64px;
      height: 64px;
      margin-bottom: var(--ww-space-2);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-md);
      color: var(--text);
    }
    .mascot {
      margin-bottom: var(--ww-space-2);
    }
    h1 {
      font-size: 32px;
      font-weight: 700;
    }
    p {
      color: var(--text-2);
      margin-bottom: var(--ww-space-3);
    }
  `,
})
export class PlaceholderPage {
  readonly heading = input('Em breve');
  readonly message = input('Essa parte do wannawatch? ainda está no forno.');
  readonly icon = input<IconName>('info');
  /** troca o ícone pelo fantasminha (404) */
  readonly mascot = input(false);
}
