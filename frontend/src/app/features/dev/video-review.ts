import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TitleVideo, TitleVideoSearch, VideoStatus } from '../../core/api/models';
import { VideoAdminApi } from '../../core/api/video-admin-api';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';

const TOKEN_KEY = 'ww-admin-token';
/** id de vídeo do YouTube: 11 caracteres de [A-Za-z0-9_-]. Só ids válidos viram URL de iframe. */
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;

/**
 * Revisão dos vídeos achados na API do YouTube (só existe em desenvolvimento: /dev/videos).
 * Mostra a miniatura e só monta o player quando a pessoa clica: dezenas de iframes travariam a página.
 */
@Component({
  selector: 'ww-video-review',
  imports: [Button, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Vídeos do Reels (teste)</h1>
    <p class="hint">
      Busca vídeos curtos no YouTube para os títulos mais populares ainda não buscados. Cada título
      gasta 1 das 100 buscas do dia. Os vídeos não são baixados: tocam pelo player do YouTube.
    </p>

    <form class="bar" (submit)="$event.preventDefault(); load()">
      <label>
        X-Admin-Token
        <input
          type="password"
          autocomplete="off"
          [value]="token()"
          (input)="setToken($any($event.target).value)"
        />
      </label>
      <button wwButton variant="outline" type="submit" [disabled]="busy() || !token()">
        Carregar
      </button>
      <label>
        Títulos
        <input
          type="number"
          min="1"
          max="20"
          [value]="limit()"
          (input)="limit.set(+$any($event.target).value || 1)"
        />
      </label>
      <button
        wwButton
        variant="primary"
        type="button"
        [disabled]="busy() || !token()"
        (click)="discover()"
      >
        Buscar no YouTube
      </button>
    </form>

    <p class="status" role="status">{{ message() }}</p>

    @if (searches().length) {
      <dl class="stats">
        <div>
          <dt>Títulos</dt>
          <dd>{{ stats().titles }}</dd>
        </div>
        <div>
          <dt>Vídeos</dt>
          <dd>{{ stats().videos }}</dd>
        </div>
        <div>
          <dt>Verticais</dt>
          <dd>{{ stats().vertical }}</dd>
        </div>
        <div>
          <dt>Duração média</dt>
          <dd>{{ duration(stats().avgSeconds) }}</dd>
        </div>
        <div>
          <dt>Aprovados</dt>
          <dd>{{ stats().approved }}</dd>
        </div>
      </dl>
      <label class="check">
        <input
          type="checkbox"
          [checked]="onlyVertical()"
          (change)="onlyVertical.set(!onlyVertical())"
        />
        Só verticais
      </label>
    }

    @for (s of searches(); track s.titleId) {
      <section [attr.aria-labelledby]="'t-' + s.titleId">
        <h2 [id]="'t-' + s.titleId">
          {{ s.title }}
          @if (s.year) {
            <span>({{ s.year }})</span>
          }
          <small>{{ s.mediaType === 'SERIES' ? 'Série' : 'Filme' }}</small>
        </h2>
        <p class="hint">Busca: “{{ s.query }}” · {{ s.resultsCount }} resultado(s)</p>
        <ul class="grid">
          @for (v of visible(s.videos); track v.id) {
            <li class="card" [class.v]="v.vertical" [attr.data-status]="v.status">
              @if (playing() === v.id && embed(v); as src) {
                <iframe
                  [src]="src"
                  [title]="'Vídeo: ' + (v.videoTitle ?? s.title)"
                  allow="autoplay; encrypted-media; picture-in-picture"
                  allowfullscreen
                ></iframe>
              } @else {
                <button
                  type="button"
                  class="thumb"
                  (click)="playing.set(v.id)"
                  [attr.aria-label]="'Tocar ' + (v.videoTitle ?? 'vídeo')"
                >
                  <img
                    [src]="'https://i.ytimg.com/vi/' + v.externalKey + '/hqdefault.jpg'"
                    alt=""
                    loading="lazy"
                  />
                  <ww-icon name="play" [size]="28" />
                </button>
              }
              <p class="vt">{{ v.videoTitle }}</p>
              <p class="meta">
                {{ v.channelTitle }} · {{ duration(v.durationSeconds) }} ·
                {{ v.vertical ? 'Vertical' : 'Horizontal' }}
                @if (v.blockedInRegion) {
                  · <strong>Bloqueado no BR</strong>
                }
                @if (!v.embeddable) {
                  · <strong>Não embutível</strong>
                }
              </p>
              <div class="acts">
                <button
                  wwButton
                  size="sm"
                  variant="outline"
                  type="button"
                  [attr.aria-pressed]="v.status === 'APPROVED'"
                  (click)="set(v, 'APPROVED')"
                >
                  Aprovar
                </button>
                <button
                  wwButton
                  size="sm"
                  variant="outline"
                  type="button"
                  [attr.aria-pressed]="v.status === 'REJECTED'"
                  (click)="set(v, 'REJECTED')"
                >
                  Reprovar
                </button>
                <a
                  [href]="'https://www.youtube.com/watch?v=' + v.externalKey"
                  target="_blank"
                  rel="noopener"
                >
                  YouTube<span class="visually-hidden"> (abre em outra aba)</span>
                </a>
              </div>
            </li>
          } @empty {
            <li class="hint">Nenhum vídeo.</li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
      padding: var(--ww-space-6) var(--ww-page-x) var(--ww-space-14);
    }
    h1 {
      font: 700 28px/1.2 var(--ww-font-display);
      margin: 0 0 var(--ww-space-2);
    }
    h2 {
      font: 700 20px/1.3 var(--ww-font-display);
      margin: var(--ww-space-8) 0 0;
    }
    h2 span,
    h2 small,
    .hint,
    .meta {
      color: var(--ww-gray-text);
      font-weight: 400;
    }
    h2 small {
      font-size: 13px;
      margin-left: var(--ww-space-2);
    }
    .hint {
      margin: var(--ww-space-1) 0;
      font-size: 14px;
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: var(--ww-space-3);
      margin-top: var(--ww-space-4);
    }
    label {
      display: grid;
      gap: var(--ww-space-1);
      font-size: 13px;
    }
    input[type='password'],
    input[type='number'] {
      min-height: var(--ww-touch);
      padding: 0 var(--ww-space-3);
      font: inherit;
      border: var(--ww-hairline) solid var(--ww-border);
      border-radius: var(--ww-radius-sm);
    }
    input[type='number'] {
      width: 80px;
    }
    .check {
      display: flex;
      align-items: center;
      gap: var(--ww-space-2);
      min-height: var(--ww-touch);
    }
    .status {
      min-height: 1.5em;
      font-size: 14px;
    }
    .stats {
      display: flex;
      flex-wrap: wrap;
      gap: var(--ww-space-6);
      margin: var(--ww-space-4) 0;
    }
    dt {
      font-size: 12px;
      color: var(--ww-gray-text);
    }
    dd {
      margin: 0;
      font: 700 22px var(--ww-font-display);
    }
    .grid {
      list-style: none;
      padding: 0;
      margin: var(--ww-space-3) 0 0;
      display: grid;
      gap: var(--ww-space-4);
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    }
    .card {
      display: grid;
      align-content: start;
      gap: var(--ww-space-2);
      padding: var(--ww-space-3);
      border: 2px solid var(--ww-border);
      border-radius: var(--ww-radius-md);
    }
    .card[data-status='APPROVED'] {
      border-color: #1f7a3a;
    }
    .card[data-status='REJECTED'] {
      opacity: 0.55;
    }
    .thumb,
    iframe {
      width: 100%;
      aspect-ratio: 16 / 9;
      border: 0;
      border-radius: var(--ww-radius-sm);
      background: #000;
    }
    .v .thumb,
    .v iframe {
      aspect-ratio: 9 / 16;
    }
    .thumb {
      position: relative;
      display: grid;
      place-items: center;
      padding: 0;
      overflow: hidden;
      color: #fff;
      cursor: pointer;
    }
    .thumb img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .thumb ww-icon {
      position: relative;
      padding: 14px;
      border-radius: 50%;
      background: rgba(0, 0, 0, 0.6);
    }
    .thumb:focus-visible {
      outline: 3px solid var(--ww-pink-strong);
      outline-offset: 2px;
    }
    .vt {
      margin: 0;
      font-weight: 600;
      font-size: 14px;
    }
    .meta {
      margin: 0;
      font-size: 13px;
    }
    .acts {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--ww-space-2);
    }
    .acts a {
      color: var(--ww-pink-strong);
      font-size: 14px;
      min-height: var(--ww-touch);
      display: inline-flex;
      align-items: center;
    }
    [aria-pressed='true'] {
      background: var(--ww-graphite);
      color: var(--ww-white);
    }
  `,
})
export class VideoReview {
  private readonly api = inject(VideoAdminApi);
  private readonly sanitizer = inject(DomSanitizer);

  readonly token = signal(readToken());
  readonly limit = signal(5);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly searches = signal<TitleVideoSearch[]>([]);
  readonly onlyVertical = signal(false);
  readonly playing = signal<number | null>(null);

  readonly stats = computed(() => {
    const all = this.searches().flatMap((s) => s.videos);
    const timed = all.filter((v) => v.durationSeconds != null);
    const total = timed.reduce((sum, v) => sum + (v.durationSeconds ?? 0), 0);
    return {
      titles: this.searches().length,
      videos: all.length,
      vertical: all.filter((v) => v.vertical).length,
      avgSeconds: timed.length ? Math.round(total / timed.length) : null,
      approved: all.filter((v) => v.status === 'APPROVED').length,
    };
  });

  constructor() {
    if (this.token()) this.load();
  }

  setToken(value: string): void {
    this.token.set(value);
    try {
      sessionStorage.setItem(TOKEN_KEY, value);
    } catch {
      // sem storage: só não lembra o token
    }
  }

  load(): void {
    this.busy.set(true);
    this.api.list(this.token()).subscribe({
      next: (list) => {
        this.searches.set(list);
        this.busy.set(false);
        this.message.set(list.length ? '' : 'Nenhum título buscado ainda.');
      },
      error: (e) => this.fail(e),
    });
  }

  discover(): void {
    this.busy.set(true);
    this.message.set('Buscando no YouTube…');
    this.api.discover(this.token(), Math.min(20, Math.max(1, this.limit()))).subscribe({
      next: (r) => {
        const done = `${r.titlesSearched} título(s) buscado(s), ${r.videosFound} vídeo(s).`;
        this.load();
        this.message.set(r.error ? `${done} Parou: ${r.error}` : done);
      },
      error: (e) => this.fail(e),
    });
  }

  set(video: TitleVideo, status: VideoStatus): void {
    const next = video.status === status ? 'PENDING' : status;
    this.api.setStatus(this.token(), video.id, next).subscribe({
      next: () =>
        this.searches.update((list) =>
          list.map((s) => ({
            ...s,
            videos: s.videos.map((v) => (v.id === video.id ? { ...v, status: next } : v)),
          })),
        ),
      error: (e) => this.fail(e),
    });
  }

  visible(videos: TitleVideo[]): TitleVideo[] {
    return this.onlyVertical() ? videos.filter((v) => v.vertical) : videos;
  }

  embed(v: TitleVideo): SafeResourceUrl | null {
    if (!YOUTUBE_ID.test(v.externalKey)) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube-nocookie.com/embed/${v.externalKey}?autoplay=1&playsinline=1&rel=0&cc_load_policy=1`,
    );
  }

  duration(seconds: number | null): string {
    if (seconds == null) return '—';
    const m = Math.floor(seconds / 60);
    const s = String(seconds % 60).padStart(2, '0');
    return `${m}:${s}`;
  }

  private fail(e: unknown): void {
    this.busy.set(false);
    const status = e instanceof HttpErrorResponse ? e.status : 0;
    const detail = e instanceof HttpErrorResponse ? e.error?.detail : null;
    this.message.set(
      status === 403
        ? 'Token de admin inválido.'
        : status === 404
          ? 'Endpoints de admin desligados: preencha ADMIN_TOKEN no .env.'
          : (detail ?? 'Não deu para falar com o back-end.'),
    );
  }
}

function readToken(): string {
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? '';
  } catch {
    return '';
  }
}
