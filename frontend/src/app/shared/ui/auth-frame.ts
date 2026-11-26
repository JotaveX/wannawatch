import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Ilustração do Bu de cada etapa de entrada/cadastro. */
export type AuthArt = 'entrar' | 'cadastro' | 'favoritos' | 'email';

/** Cor de fundo de cada ilustração: a tela inteira é pintada com ela para a imagem "vazar". */
const ART_COLORS: Record<AuthArt, string> = {
  entrar: '#716dca',
  cadastro: '#f29e4c',
  favoritos: '#e8577e',
  email: '#5b8def',
};

/**
 * Moldura do login e do cadastro: o Bu ocupa a tela inteira, fixo ao fundo, e o conteúdo fica num
 * painel à direita, de cima a baixo da tela, que rola por cima dele. No celular a ilustração vira
 * uma faixa em cima e o painel sobe como uma folha. `wide` abre espaço para a etapa de escolher títulos.
 */
@Component({
  selector: 'ww-auth-frame',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.wide]': 'wide()',
    '[style.--art-color]': 'color()',
  },
  template: `
    <div class="art">
      <!-- recria a imagem a cada etapa para a entrada animar de novo -->
      @for (s of [src()]; track s) {
        <img [src]="s" alt="" width="1254" height="1254" />
      }
    </div>
    <div class="panel"><ng-content /></div>
  `,
  styles: `
    :host {
      --panel-w: min(560px, 46vw);
      display: block;
      min-height: 100dvh;
      background-color: var(--art-color);
      transition: background-color var(--ww-slow) var(--ww-ease-out);
    }
    :host(.wide) {
      --panel-w: min(920px, 64vw);
    }
    .art {
      position: fixed;
      inset: 0;
      pointer-events: none;
    }
    img {
      position: absolute;
      bottom: 0;
      left: 0;
      /* cabe na parte da tela que o painel deixa livre */
      width: min(100dvh, 100vw - var(--panel-w) - var(--ww-space-4));
      height: auto;
      animation: entra var(--ww-slow) var(--ww-ease-out);
    }
    .panel {
      --panel-bg: var(--surface);
      position: relative;
      display: flex;
      flex-direction: column;
      justify-content: center;
      width: var(--panel-w);
      min-height: 100dvh;
      margin-left: auto;
      padding: var(--ww-space-10) clamp(var(--ww-space-6), 4vw, 72px);
      /* encostado no topo, no fundo e na direita: só a borda de dentro é arredondada */
      border-radius: var(--ww-radius-xl) 0 0 var(--ww-radius-xl);
      background: var(--surface);
      box-shadow: var(--ww-shadow-lift);
    }
    :host(.wide) .panel {
      justify-content: flex-start;
      padding: var(--ww-space-8) clamp(var(--ww-space-4), 3vw, var(--ww-space-8));
    }
    @keyframes entra {
      from {
        opacity: 0;
        transform: translateY(16px);
      }
    }
    @media (max-width: 899px) {
      :host {
        display: flex;
        flex-direction: column;
      }
      .art {
        position: relative;
        inset: auto;
        flex-shrink: 0;
        height: 176px;
      }
      img {
        width: auto;
        height: 100%;
      }
      .panel,
      :host(.wide) .panel {
        flex: 1;
        justify-content: flex-start;
        width: 100%;
        min-height: 0;
        padding: var(--ww-space-6) var(--ww-page-x) var(--ww-space-10);
        border-radius: var(--ww-radius-xl) var(--ww-radius-xl) 0 0;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      :host {
        transition: none;
      }
      img {
        animation: none;
      }
    }
  `,
})
export class AuthFrame {
  readonly art = input.required<AuthArt>();
  readonly wide = input(false);

  protected readonly src = computed(() => `mascot/auth/bu-${this.art()}.svg`);
  protected readonly color = computed(() => ART_COLORS[this.art()]);
}
