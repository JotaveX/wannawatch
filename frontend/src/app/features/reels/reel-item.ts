import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ReelCard } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { LikesStore } from '../../core/social/likes-store';
import { Icon } from '../../shared/ui/icon';
import { Poster } from '../../shared/ui/poster';
import { LikedBy } from './liked-by';

/** id de vídeo do YouTube: 11 caracteres de [A-Za-z0-9_-]. Só ids válidos viram URL de iframe. */
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
const YOUTUBE_ORIGIN = 'https://www.youtube-nocookie.com';

/** Estados do player do YouTube (postMessage). */
const ENDED = 0;
const PLAYING = 1;
const PAUSED = 2;
const BUFFERING = 3;
/** Espera o primeiro instante tocando (antes disso o iframe mostra a capa do YouTube). */
const REVEAL_AFTER_S = 0.3;
/**
 * Toda vez que o vídeo volta a tocar (começo, retomar, loop), o YouTube desenha por ~4s um ícone de
 * pausa no meio do vídeo. O nosso botão do meio fica por cima dele esse tempo todo.
 */
const COVER_MS = 4500;
/** O loop é nosso: volta ao início um pouco antes do fim, para a tela final do YouTube não aparecer. */
const RESTART_BEFORE_END_S = 0.6;

/**
 * Um reel. Sem vídeo: a sinopse em destaque sobre a imagem escurecida. Com vídeo: o vídeo ocupa o
 * reel inteiro (sem o pôster atrás), com só o título por cima. Ações à direita nos dois casos.
 *
 * O player só existe no reel ativo (um iframe por vez). Toca sozinho e sem som; com
 * `prefers-reduced-motion` espera o play. A interface do YouTube não aparece: o iframe só fica
 * visível depois que o vídeo começa a tocar; é maior que o reel, então o título e as marcas de cima
 * e de baixo ficam cortados para fora; o ícone que ele desenha no meio ao voltar a tocar fica sob o
 * nosso botão do meio; e o loop é nosso, antes da tela final. Pausado (o quadro parado é limpo), o
 * vídeo continua na tela com o nosso botão de play. Os controles são nossos (o iframe
 * não recebe clique, para a rolagem funcionar por cima): som, volume e pausa vão por postMessage.
 */
@Component({
  selector: 'ww-reel-item',
  imports: [RouterLink, Icon, Poster, LikedBy],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- quem curtiu / motivo da recomendação descreve o reel para leitores de tela -->
    <article
      [class.has-video]="!!embed()"
      [attr.aria-labelledby]="'reel-' + card().id"
      [attr.aria-describedby]="'why-' + card().id"
    >
      @if (embed(); as src) {
        @if (playing()) {
          <iframe
            #player
            class="video"
            [class.on]="onScreen()"
            [src]="src"
            [title]="'Vídeo de ' + card().title"
            tabindex="-1"
            allow="autoplay; encrypted-media"
            (load)="listen()"
          ></iframe>
        }
      } @else {
        <ww-poster
          class="bg"
          ratio="fill"
          [image]="card().poster"
          [widths]="[342, 780]"
          sizes="480px"
          [seed]="card().id"
          [showTitle]="false"
          [eager]="eager()"
        />
      }
      @if (!embed()) {
        <div class="shade" aria-hidden="true"></div>
      }

      @if (embed()) {
        <!-- botão do meio: play quando pausado; logo depois de voltar a tocar, cobre o ícone do YouTube -->
        @if (paused() || covering()) {
          <button
            type="button"
            class="round big"
            tabindex="-1"
            aria-hidden="true"
            (click)="togglePause()"
          >
            <ww-icon [name]="paused() ? 'play' : 'pause'" [size]="32" />
          </button>
        }
        <div class="controls">
          <button
            type="button"
            class="round sm"
            [attr.aria-pressed]="paused()"
            aria-label="Pausar vídeo"
            (click)="togglePause()"
          >
            <ww-icon [name]="paused() ? 'play' : 'pause'" [size]="20" />
          </button>
          <div class="volume">
            <button
              type="button"
              class="round sm"
              [attr.aria-pressed]="!muted()"
              aria-label="Som do vídeo"
              (click)="muteToggle.emit()"
            >
              <ww-icon [name]="level() ? 'volume' : 'volume-off'" [size]="20" />
            </button>
            <input
              type="range"
              min="0"
              max="100"
              step="1"
              aria-label="Volume"
              [value]="level()"
              [attr.aria-valuetext]="level() + '%'"
              [style.--level]="level() + '%'"
              (input)="volumeChange.emit(+$any($event.target).value)"
            />
          </div>
        </div>
      }

      <div class="text">
        <ww-liked-by
          [id]="'why-' + card().id"
          [titleId]="card().id"
          [reason]="card().reason?.text"
          [floating]="true"
        />
        @if (!embed()) {
          <span class="kicker">Sinopse</span>
          <p class="synopsis" [id]="'syn-' + card().id">{{ card().overview }}</p>
        }
        <div class="name">
          <h2 [id]="'reel-' + card().id">{{ card().title }}</h2>
          <span>{{ meta() }}</span>
        </div>
      </div>

      <div class="actions">
        <div>
          <button
            type="button"
            class="round"
            [class.on]="liked()"
            [attr.aria-pressed]="liked()"
            aria-label="Quero ver"
            (click)="like.emit()"
          >
            <ww-icon [name]="liked() ? 'heart-filled' : 'heart'" [size]="24" />
          </button>
          @if (likeCount()) {
            <span aria-hidden="true">{{ likeCount() }}</span>
          }
        </div>
        <div>
          <button
            type="button"
            class="round"
            [class.on]="saved()"
            [attr.aria-pressed]="saved()"
            aria-label="Salvar na lista"
            (click)="save.emit()"
          >
            <ww-icon [name]="saved() ? 'bookmark-filled' : 'bookmark'" />
          </button>
        </div>
        <div>
          <button type="button" class="round" aria-label="Compartilhar" (click)="share.emit()">
            <ww-icon name="send" />
          </button>
        </div>
        <div>
          <a class="round" [routerLink]="link()" [attr.aria-label]="'Detalhes de ' + card().title">
            <ww-icon name="info" />
          </a>
        </div>
      </div>
    </article>
  `,
  styleUrl: './reel-item.css',
})
export class ReelItem {
  readonly card = input.required<ReelCard>();
  readonly index = input.required<number>();
  readonly liked = input(false);
  readonly saved = input(false);
  readonly eager = input(false);
  /** reel na tela: só ele monta o player */
  readonly active = input(false);
  /** o som vale para o feed todo (quem ligou num reel continua com som no próximo) */
  readonly muted = input(true);
  /** volume de 0 a 100, também do feed todo */
  readonly volume = input(100);
  readonly like = output<void>();
  readonly save = output<void>();
  readonly share = output<void>();
  readonly muteToggle = output<void>();
  readonly volumeChange = output<number>();
  /** avisa quando este reel ocupa a maior parte da tela */
  readonly visible = output<number>();

  private readonly likes = inject(LikesStore);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly player = viewChild<ElementRef<HTMLIFrameElement>>('player');
  private readonly reducedMotion =
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  protected readonly paused = signal(this.reducedMotion);
  /** já tocou nesta passagem pelo reel (com movimento reduzido, só depois do play) */
  private readonly started = signal(!this.reducedMotion);
  /** o vídeo está tocando de verdade (fora disso o iframe mostraria a interface do YouTube) */
  protected readonly onScreen = signal(false);
  /** voltou a tocar há pouco: o botão do meio cobre o ícone que o YouTube desenha ali */
  protected readonly covering = signal(false);
  private coverTimer: ReturnType<typeof setTimeout> | undefined;
  /** último estado, tempo e duração que o player mandou */
  private playerState = -1;
  private currentTime = 0;
  private duration = 0;
  /** já apareceu nesta passagem: depois de um seek não espera de novo o título sumir */
  private revealed = false;
  /** quando mandamos voltar ao início: o player ainda manda o tempo antigo por um instante */
  private restartedAt = 0;

  /** quantas pessoas querem ver (os amigos + eu) */
  protected readonly likeCount = computed(
    () => this.likes.friends(this.card().id).length + (this.liked() ? 1 : 0),
  );
  protected readonly meta = computed(() =>
    [metaLine(this.card()), this.card().year].filter(Boolean).join(' · '),
  );
  protected readonly link = computed(() => titleLink(this.card()));
  /** o que o volume mostra: sem som conta como zero */
  protected readonly level = computed(() => (this.muted() ? 0 : this.volume()));
  protected readonly playing = computed(() => this.active() && this.started());
  /** URL do embed; a do iframe não muda com som/pausa (isso vai por mensagem), senão o vídeo recomeçaria */
  protected readonly embed = computed<SafeResourceUrl | null>(() => {
    const key = this.card().video?.key;
    if (!key || !YOUTUBE_ID.test(key)) return null;
    const params = new URLSearchParams({
      autoplay: '1',
      mute: '1',
      playsinline: '1',
      controls: '0',
      disablekb: '1',
      fs: '0',
      iv_load_policy: '3',
      rel: '0',
      cc_load_policy: '1',
      enablejsapi: '1',
    });
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `${YOUTUBE_ORIGIN}/embed/${key}?${params}`,
    );
  });

  constructor() {
    // saiu da tela: na volta começa de novo
    effect(() => {
      if (this.active()) return;
      untracked(() => {
        this.started.set(!this.reducedMotion);
        this.paused.set(this.reducedMotion);
        this.onScreen.set(false);
        this.covering.set(false);
        clearTimeout(this.coverTimer);
        this.playerState = -1;
        this.currentTime = 0;
        this.duration = 0;
        this.revealed = false;
        this.restartedAt = 0;
      });
    });
    // um comando por mudança: arrastar o volume manda só setVolume (sem repetir som e play)
    effect(() => {
      const volume = this.volume();
      untracked(() => this.send('setVolume', [volume]));
    });
    effect(() => {
      const muted = this.muted();
      untracked(() => this.send(muted ? 'mute' : 'unMute'));
    });
    // play/pause só quando muda de verdade: o autoplay já toca sozinho, e cada playVideo a mais
    // faz o YouTube desenhar o ícone dele no meio do vídeo
    let wasPaused = this.paused();
    effect(() => {
      const paused = this.paused();
      if (paused === wasPaused) return;
      wasPaused = paused;
      untracked(() => {
        this.send(paused ? 'pauseVideo' : 'playVideo');
        if (paused) this.uncover();
        else this.cover();
      });
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.coverTimer));

    // o player avisa quando está pronto; comandos mandados antes disso se perdem
    const onMessage = (event: MessageEvent) => {
      const win = this.player()?.nativeElement.contentWindow;
      if (!win || event.source !== win || event.origin !== YOUTUBE_ORIGIN) return;
      try {
        this.onPlayerMessage(JSON.parse(String(event.data)));
      } catch {
        // mensagem que não é do player: ignora
      }
    };
    addEventListener('message', onMessage);
    inject(DestroyRef).onDestroy(() => removeEventListener('message', onMessage));

    const el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    if (typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) this.visible.emit(this.index());
      },
      { threshold: 0.6 },
    );
    observer.observe(el);
    inject(DestroyRef).onDestroy(() => observer.disconnect());
  }

  togglePause(): void {
    if (!this.started()) {
      this.started.set(true);
      this.paused.set(false);
      return;
    }
    this.paused.update((p) => !p);
  }

  /** Pede ao player os eventos (onReady etc.) e já manda o estado, caso ele esteja pronto. */
  protected listen(): void {
    this.player()?.nativeElement.contentWindow?.postMessage(
      JSON.stringify({ event: 'listening' }),
      YOUTUBE_ORIGIN,
    );
    this.sync();
  }

  /** Estado, tempo e duração que o player manda: decidem quando o vídeo aparece e o loop. */
  private onPlayerMessage(data: { event?: string; info?: unknown }): void {
    if (data?.event === 'onReady') this.sync();
    const info = (data?.event === 'infoDelivery' ? data.info : null) as {
      playerState?: number;
      currentTime?: number;
      duration?: number;
    } | null;
    const state = data?.event === 'onStateChange' ? data.info : info?.playerState;
    if (typeof state === 'number') {
      // voltou a tocar (começo, retomar, depois de carregar): o YouTube desenha o ícone dele agora
      if (state === PLAYING && this.playerState !== PLAYING) this.cover();
      this.playerState = state;
    }
    if (typeof info?.currentTime === 'number') this.currentTime = info.currentTime;
    if (typeof info?.duration === 'number' && info.duration > 0) this.duration = info.duration;

    const nearEnd =
      Date.now() - this.restartedAt > 1000 &&
      this.duration > RESTART_BEFORE_END_S * 2 &&
      this.currentTime >= this.duration - RESTART_BEFORE_END_S;
    if (this.playerState === ENDED || nearEnd) {
      this.currentTime = 0;
      this.restartedAt = Date.now();
      this.send('seekTo', [0, true]);
      this.cover();
      // perto do fim o seek continua tocando; só depois de acabar precisa do play
      if (this.playerState === ENDED && !this.paused()) this.send('playVideo');
      // acabou de vez: esconde até voltar a tocar; perto do fim o vídeo segue na tela
      if (this.playerState === ENDED) this.onScreen.set(false);
      return;
    }
    // depois de aparecer, segue na tela pausado ou carregando (o botão do meio cobre o ícone)
    const shown = this.revealed
      ? [PLAYING, PAUSED, BUFFERING].includes(this.playerState)
      : this.playerState === PLAYING && this.currentTime >= REVEAL_AFTER_S;
    if (shown) this.revealed = true;
    this.onScreen.set(shown);
  }

  private cover(): void {
    clearTimeout(this.coverTimer);
    this.covering.set(true);
    this.coverTimer = setTimeout(() => this.covering.set(false), COVER_MS);
  }

  private uncover(): void {
    clearTimeout(this.coverTimer);
    this.covering.set(false);
  }

  private send(func: string, args: unknown[] = []): void {
    this.player()?.nativeElement.contentWindow?.postMessage(
      JSON.stringify({ event: 'command', func, args }),
      YOUTUBE_ORIGIN,
    );
  }

  /** Manda o estado de som, volume e pausa para o player (quando ele fica pronto). */
  private sync(): void {
    this.send('setVolume', [this.volume()]);
    this.send(this.muted() ? 'mute' : 'unMute');
    if (this.paused()) this.send('pauseVideo');
  }
}
