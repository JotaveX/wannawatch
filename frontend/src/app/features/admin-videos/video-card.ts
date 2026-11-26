import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { TitleVideo, VideoStatus } from '../../core/api/models';
import { Icon } from '../../shared/ui/icon';

/** id de vídeo do YouTube: 11 caracteres de [A-Za-z0-9_-]. Só ids válidos viram URL de iframe. */
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;

const REVIEWED_AT = new Intl.DateTimeFormat('pt-BR', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDuration(seconds: number | null): string {
  if (seconds == null) return '—';
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/**
 * Um vídeo achado no YouTube, para revisar. Mostra a miniatura e só monta o player quando a pessoa
 * clica: dezenas de iframes travariam a página.
 *
 * mode "review" = fila (Aprovar / Reprovar, só ícone) · "history" = já revisado (selo, data e trocar/desfazer).
 */
@Component({
  selector: 'ww-video-card',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-status]': 'video().status' },
  template: `
    <div class="media">
      @if (playing() && embed(); as src) {
        <iframe
          [src]="src"
          [title]="'Vídeo: ' + name()"
          allow="autoplay; encrypted-media; picture-in-picture"
          allowfullscreen
        ></iframe>
      } @else {
        <button
          type="button"
          class="thumb"
          (click)="playing.set(true)"
          [attr.aria-label]="'Tocar ' + name()"
        >
          <img
            [src]="'https://i.ytimg.com/vi/' + video().externalKey + '/hqdefault.jpg'"
            alt=""
            loading="lazy"
            decoding="async"
          />
          <span class="play"><ww-icon name="play" [size]="22" /></span>
          <span class="dur">{{ duration() }}</span>
        </button>
      }
      @if (mode() === 'history') {
        <span class="badge">
          <ww-icon [name]="video().status === 'APPROVED' ? 'check' : 'x'" [size]="14" />
          {{ video().status === 'APPROVED' ? 'Aprovado' : 'Reprovado' }}
        </span>
      }
    </div>

    <div class="body">
      @if (label()) {
        <p class="label">{{ label() }}</p>
      }
      <p class="vt" [title]="video().videoTitle ?? ''">{{ video().videoTitle ?? 'Sem título' }}</p>
      <p class="meta">
        {{ video().channelTitle ?? 'Canal desconhecido' }}
        @if (mode() === 'history' && reviewed(); as when) {
          · {{ when }}
        }
      </p>
      @if (video().blockedInRegion || !video().embeddable) {
        <p class="warn">
          @if (video().blockedInRegion) {
            <span>Bloqueado no BR</span>
          }
          @if (!video().embeddable) {
            <span>Não embutível</span>
          }
        </p>
      }
    </div>

    <div class="acts">
      @if (mode() === 'review') {
        <button
          type="button"
          class="act no"
          title="Reprovar"
          [attr.aria-label]="'Reprovar ' + name()"
          (click)="decide.emit('REJECTED')"
        >
          <ww-icon name="x" [size]="22" />
        </button>
        <button
          type="button"
          class="act yes"
          title="Aprovar"
          [attr.aria-label]="'Aprovar ' + name()"
          (click)="decide.emit('APPROVED')"
        >
          <ww-icon name="check" [size]="22" />
        </button>
      } @else {
        @if (video().status === 'APPROVED') {
          <button type="button" class="act no" (click)="decide.emit('REJECTED')">
            <ww-icon name="x" [size]="18" /> Reprovar
          </button>
        } @else {
          <button type="button" class="act yes" (click)="decide.emit('APPROVED')">
            <ww-icon name="check" [size]="18" /> Aprovar
          </button>
        }
        <button type="button" class="act" (click)="decide.emit('PENDING')">
          <ww-icon name="undo" [size]="18" /> Voltar à fila
        </button>
      }
      <a
        class="yt"
        [href]="'https://www.youtube.com/watch?v=' + video().externalKey"
        target="_blank"
        rel="noopener"
        [attr.aria-label]="'Abrir no YouTube (outra aba): ' + name()"
      >
        <ww-icon name="external" [size]="18" />
      </a>
    </div>
  `,
  styles: `
    :host {
      --ok: #1f7a3a;
      display: flex;
      flex-direction: column;
      gap: var(--ww-space-3);
      height: 100%;
      padding: var(--ww-space-2) var(--ww-space-2) var(--ww-space-3);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-lg);
      background: var(--surface);
      transition:
        transform var(--ww-medium) var(--ww-ease-spring),
        box-shadow var(--ww-medium) var(--ww-ease-out);
    }
    @media (hover: hover) and (pointer: fine) {
      :host(:hover) {
        transform: translateY(-3px);
        box-shadow: var(--ww-shadow-lift);
      }
    }
    .media {
      position: relative;
    }
    .thumb,
    iframe {
      display: block;
      width: 100%;
      aspect-ratio: 9 / 16;
      border: 0;
      border-radius: var(--ww-radius-md);
      background: #000;
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
      transition: transform var(--ww-slow) var(--ww-ease-out);
    }
    .thumb:hover img {
      transform: scale(1.04);
    }
    .play {
      position: relative;
      display: grid;
      place-items: center;
      width: 52px;
      height: 52px;
      border-radius: 50%;
      background: var(--ww-glass-dark);
      transition: transform var(--ww-medium) var(--ww-ease-spring);
    }
    .thumb:hover .play {
      transform: scale(1.12);
    }
    .dur,
    .badge {
      position: absolute;
      padding: 2px var(--ww-space-2);
      border-radius: var(--ww-radius-pill);
      font-size: 12px;
      font-weight: 600;
    }
    .dur {
      right: var(--ww-space-2);
      bottom: var(--ww-space-2);
      background: rgba(0, 0, 0, 0.6);
      font-variant-numeric: tabular-nums;
    }
    .badge {
      top: var(--ww-space-2);
      left: var(--ww-space-2);
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 10px 4px 8px;
      background: var(--ok);
      color: #fff;
      pointer-events: none;
    }
    :host([data-status='REJECTED']) .badge {
      background: var(--ww-pink-strong);
    }
    .thumb:focus-visible,
    .act:focus-visible,
    .yt:focus-visible {
      outline: 3px solid var(--focus-ring);
      outline-offset: 2px;
    }
    .body {
      display: grid;
      gap: 2px;
      padding: 0 var(--ww-space-1);
    }
    p {
      margin: 0;
    }
    .label {
      color: var(--link);
      font-size: 12px;
      font-weight: 700;
    }
    .vt {
      display: -webkit-box;
      overflow: hidden;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      font-size: 14px;
      font-weight: 600;
      line-height: 1.35;
    }
    .meta {
      color: var(--text-2);
      font-size: 13px;
    }
    .warn {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin-top: var(--ww-space-1);
    }
    .warn span {
      padding: 2px var(--ww-space-2);
      border-radius: var(--ww-radius-pill);
      background: #fff4d1;
      color: #6b4f00;
      font-size: 12px;
      font-weight: 600;
    }
    .acts {
      display: flex;
      gap: 6px;
      margin-top: auto;
    }
    .act,
    .yt {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      min-height: var(--ww-touch);
      border: var(--ww-hairline) solid var(--border);
      border-radius: var(--ww-radius-pill);
      background: var(--surface);
      color: var(--text);
      font: 600 13px var(--ww-font-ui);
      cursor: pointer;
      transition:
        background-color var(--ww-medium) var(--ww-ease-out),
        color var(--ww-medium) var(--ww-ease-out),
        border-color var(--ww-medium) var(--ww-ease-out),
        transform var(--ww-fast) var(--ww-ease-out);
    }
    .act {
      flex: 1;
      min-width: 0;
      padding: 0 var(--ww-space-2);
    }
    .act:active,
    .yt:active {
      transform: scale(0.95);
    }
    .act:hover {
      background: var(--hover);
    }
    .act.no {
      color: var(--ww-pink-strong);
    }
    .act.no:hover {
      border-color: var(--ww-pink-strong);
      background: var(--ww-pink-strong);
      color: #fff;
    }
    .act.yes {
      border-color: var(--ok);
      background: var(--ok);
      color: #fff;
    }
    .act.yes:hover {
      background: #17602d;
    }
    .yt {
      flex: none;
      width: var(--ww-touch);
      color: var(--text-2);
    }
    .yt:hover {
      color: var(--text);
      background: var(--hover);
    }
  `,
})
export class VideoCard {
  private readonly sanitizer = inject(DomSanitizer);

  readonly video = input.required<TitleVideo>();
  /** nome do título, mostrado em cima (no histórico os cards não ficam agrupados) */
  readonly label = input<string | null>(null);
  readonly mode = input<'review' | 'history'>('review');
  readonly decide = output<VideoStatus>();

  protected readonly playing = signal(false);
  protected readonly name = computed(() => this.video().videoTitle ?? 'vídeo');
  protected readonly duration = computed(() => formatDuration(this.video().durationSeconds));
  protected readonly reviewed = computed(() => {
    const at = this.video().reviewedAt;
    return at ? REVIEWED_AT.format(new Date(at)) : null;
  });
  protected readonly embed = computed<SafeResourceUrl | null>(() => {
    const key = this.video().externalKey;
    if (!YOUTUBE_ID.test(key)) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube-nocookie.com/embed/${key}?autoplay=1&playsinline=1&rel=0&cc_load_policy=1`,
    );
  });
}
