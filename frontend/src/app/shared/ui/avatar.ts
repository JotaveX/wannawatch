import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { UserRef } from '../../core/api/models';
import { initialsOf } from '../../core/auth/auth-store';
import { PhotoStore } from '../../core/social/photo-store';
import { userColors } from '../../core/social/user-colors';
import { BU_MASCOTS } from './mascots';

/**
 * Avatar de um usuário: a foto (se enviou neste aparelho), senão o Bu que escolheu sobre a
 * cor dele, senão as iniciais na cor dele. `photo` força uma foto (prévia no perfil).
 * Decorativo: quem usa diz o nome por perto (texto ou aria-label).
 */
@Component({
  selector: 'ww-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.background]': 'background()',
    '[style.color]': 'colors().fg',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() * 0.34',
  },
  template: `
    @if (image(); as src) {
      <img [src]="src" alt="" [width]="size()" [height]="size()" decoding="async" />
    } @else {
      {{ initials() }}
    }
    <ng-content />
  `,
  styles: `
    :host {
      position: relative;
      flex-shrink: 0;
      display: inline-grid;
      place-items: center;
      border-radius: 50%;
      font-family: var(--ww-font-display);
      font-weight: 700;
      letter-spacing: -0.02em;
      line-height: 1;
    }
    img {
      display: block;
      width: 100%;
      height: 100%;
      border-radius: 50%;
      object-fit: cover;
    }
  `,
})
export class Avatar {
  readonly user = input.required<UserRef>();
  readonly size = input(40);
  /** prévia no perfil: undefined = a foto salva; null = sem foto; string = esta foto */
  readonly photo = input<string | null | undefined>(undefined);

  private readonly photos = inject(PhotoStore);
  protected readonly initials = computed(() => initialsOf(this.user().displayName));
  protected readonly colors = computed(() => userColors(this.user()));
  private readonly mascot = computed(() => {
    const index = this.user().mascot;
    return index == null ? null : (BU_MASCOTS[index] ?? null);
  });
  protected readonly image = computed(() => {
    const preview = this.photo();
    const photo = preview === undefined ? this.photos.photo(this.user().id) : preview;
    return photo ?? this.mascot()?.avatar ?? null;
  });
  private readonly hasPhoto = computed(() => {
    const preview = this.photo();
    return !!(preview === undefined ? this.photos.photo(this.user().id) : preview);
  });
  protected readonly background = computed(() => (this.hasPhoto() ? 'transparent' : this.colors().bg));
}
