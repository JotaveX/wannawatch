import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TitleCard } from '../../core/api/models';
import { metaLine } from '../../core/format';
import { titleLink } from '../../core/title-link';
import { LikesStore } from '../../core/social/likes-store';
import { Icon } from '../../shared/ui/icon';
import { Poster } from '../../shared/ui/poster';
import { LikedBy } from './liked-by';

/** Um reel: a sinopse em destaque sobre a imagem escurecida, com as ações à direita. */
@Component({
  selector: 'ww-reel-item',
  imports: [RouterLink, Icon, Poster, LikedBy],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article [attr.aria-labelledby]="'reel-' + card().id">
      <ww-poster class="bg" ratio="fill" [image]="card().poster" [widths]="[342, 780]" sizes="480px"
                 [seed]="card().id" [showTitle]="false" [eager]="eager()" />
      <div class="shade" aria-hidden="true"></div>

      <div class="text">
        <ww-liked-by [titleId]="card().id" [reason]="card().reason?.text" />
        <span class="kicker">Sinopse</span>
        <p class="synopsis">{{ card().overview }}</p>
        <div class="name">
          <h2 [id]="'reel-' + card().id">{{ card().title }}</h2>
          <span>{{ meta() }}</span>
        </div>
        <div class="chips">
          <a class="outline" [routerLink]="link()">Ver detalhes</a>
        </div>
      </div>

      <div class="actions">
        <div>
          <button type="button" class="round" [class.on]="liked()" [attr.aria-pressed]="liked()"
                  aria-label="Quero ver" (click)="like.emit()">
            <ww-icon [name]="liked() ? 'heart-filled' : 'heart'" [size]="24" />
          </button>
          <span aria-hidden="true">{{ likeCount() || 'Quero ver' }}</span>
        </div>
        <div>
          <button type="button" class="round" [class.on]="saved()" [attr.aria-pressed]="saved()"
                  aria-label="Salvar na lista" (click)="save.emit()">
            <ww-icon [name]="saved() ? 'bookmark-filled' : 'bookmark'" />
          </button>
          <span aria-hidden="true">{{ saved() ? 'Salvo' : 'Salvar' }}</span>
        </div>
        <div>
          <button type="button" class="round" aria-label="Compartilhar" (click)="share.emit()">
            <ww-icon name="send" />
          </button>
          <span aria-hidden="true">Enviar</span>
        </div>
        <div>
          <a class="round" [routerLink]="link()" [attr.aria-label]="'Detalhes de ' + card().title">
            <ww-icon name="info" />
          </a>
          <span aria-hidden="true">Detalhes</span>
        </div>
      </div>
    </article>
  `,
  styleUrl: './reel-item.css',
})
export class ReelItem {
  readonly card = input.required<TitleCard>();
  readonly index = input.required<number>();
  readonly liked = input(false);
  readonly saved = input(false);
  readonly eager = input(false);
  readonly like = output<void>();
  readonly save = output<void>();
  readonly share = output<void>();
  /** avisa quando este reel ocupa a maior parte da tela */
  readonly visible = output<number>();

  private readonly likes = inject(LikesStore);
  /** quantas pessoas querem ver (os amigos + eu) */
  protected readonly likeCount = computed(() => this.likes.friends(this.card().id).length + (this.liked() ? 1 : 0));
  protected readonly meta = computed(() => [metaLine(this.card()), this.card().year].filter(Boolean).join(' · '));
  protected readonly link = computed(() => titleLink(this.card()));

  constructor() {
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
}
