import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Logo } from '../../shared/ui/logo';
import { Mascot } from '../../shared/ui/mascot';

/**
 * Sobre e créditos. A TMDB exige o logo dela (menos destacado que o nosso) e o aviso em inglês;
 * os dados de "onde assistir" vêm da JustWatch e também precisam de crédito.
 */
@Component({
  selector: 'ww-about-page',
  imports: [Logo, Mascot],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="ww-stagger">
      <header>
        <ww-mascot [size]="112" />
        <h1><ww-logo [size]="40" /></h1>
        <p class="lead">Descubra o que assistir sozinho ou com a galera: swipe, reels de sinopses e listas que dão match.</p>
      </header>

      <section aria-labelledby="creditos">
        <h2 id="creditos">Créditos</h2>

        <div class="credit">
          <a href="https://www.themoviedb.org" target="_blank" rel="noopener" class="tmdb">
            <img src="attribution/tmdb-logo.svg" alt="The Movie Database (TMDB)" width="180" height="24" />
          </a>
          <p lang="en">This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
          <p>Títulos, sinopses, imagens, elenco e equipe vêm da The Movie Database (TMDB).</p>
        </div>

        <div class="credit">
          <p>
            Os dados de onde assistir são fornecidos pela
            <a href="https://www.justwatch.com" target="_blank" rel="noopener">JustWatch</a>.
          </p>
        </div>
      </section>
    </article>
  `,
  styles: `
    :host {
      display: block;
      padding: var(--ww-space-14) var(--ww-page-x);
    }
    article {
      max-width: 640px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-10);
    }
    header {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-4);
    }
    .lead {
      font-size: 18px;
      color: var(--text-2);
    }
    section {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-4);
    }
    h2 {
      font-size: 22px;
    }
    .credit {
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-2);
      padding: var(--ww-space-5);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-md);
      color: var(--text-2);
      font-size: 14px;
    }
    .tmdb {
      display: inline-flex;
      min-height: var(--ww-touch);
      align-items: center;
      align-self: flex-start;
    }
    .tmdb img {
      height: 20px;
      width: auto;
    }
  `,
})
export class AboutPage {}
