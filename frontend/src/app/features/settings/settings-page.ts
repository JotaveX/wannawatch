import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog-api';
import { Profile, TitleCard, UserRef } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';
import { AuthStore } from '../../core/auth/auth-store';
import { PhotoError, preparePhoto } from '../../core/social/photo-file';
import { USER_COLORS } from '../../core/social/user-colors';
import { Avatar } from '../../shared/ui/avatar';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { FavoriteTitlePicker } from './favorite-title-picker';
import { BU_MASCOTS } from '../../shared/ui/mascots';

/** O que aparece no lugar do avatar: iniciais, uma pose do Bu (índice) ou a foto enviada. */
type AvatarChoice = 'initials' | 'photo' | number;

const NAME_MAX = 40;

/**
 * Ajustes do perfil: nome, cor, avatar (Bu ou foto), gênero e título favoritos, e a conta.
 * O formulário é um rascunho: o cabeçalho do card mostra a prévia e "Salvar" só acende com mudança.
 */
@Component({
  selector: 'ww-settings-page',
  imports: [RouterLink, Avatar, Button, Icon, FavoriteTitlePicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.css',
})
export class SettingsPage {
  private readonly api = inject(SocialApi);
  private readonly catalog = inject(CatalogApi);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly isAdmin = this.auth.isAdmin;
  protected readonly colors = USER_COLORS;
  protected readonly mascots = BU_MASCOTS;
  protected readonly nameMax = NAME_MAX;

  protected readonly profile = signal<Profile | null>(null);
  protected readonly loadError = signal(false);

  // --- rascunho ---
  protected readonly name = signal('');
  protected readonly color = signal(0);
  protected readonly avatar = signal<AvatarChoice>('initials');
  /** o que aparece no quadradinho da foto: a URL salva ou a prévia da que acabou de escolher */
  protected readonly photo = signal<string | null>(null);
  /** foto escolhida e ainda não enviada (vai no "Salvar") */
  private readonly pendingPhoto = signal<Blob | null>(null);
  protected readonly genreId = signal<number | null>(null);
  protected readonly favorite = signal<TitleCard | null>(null);

  protected readonly saving = signal(false);
  protected readonly status = signal<{ kind: 'ok' | 'error'; text: string } | null>(null);
  protected readonly photoError = signal<string | null>(null);

  protected readonly genres = toSignal(
    this.catalog.genres().pipe(
      map((all) =>
        all.filter((g) => g.titleCount > 0).sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
      ),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );

  /** O usuário como vai ficar: alimenta a prévia do cabeçalho. */
  protected readonly preview = computed<UserRef | null>(() => {
    const p = this.profile();
    if (!p) return null;
    return {
      ...p.user,
      displayName: this.cleanName() || p.user.displayName,
      color: this.color(),
      mascot: this.mascotToSave(),
    };
  });
  protected readonly previewPhoto = computed(() =>
    this.avatar() === 'photo' ? this.photo() : null,
  );

  private readonly cleanName = computed(() => this.name().trim().replace(/\s+/g, ' '));
  /** Com foto, o Bu salvo fica guardado: volta a valer se a pessoa tirar a foto. */
  private readonly mascotToSave = computed(() => {
    const a = this.avatar();
    return typeof a === 'number' ? a : a === 'photo' ? (this.profile()?.user.mascot ?? null) : null;
  });

  protected readonly nameError = computed(() =>
    this.cleanName() ? null : 'Seu nome não pode ficar vazio.',
  );

  protected readonly dirty = computed(() => {
    const p = this.profile();
    if (!p) return false;
    return (
      this.cleanName() !== p.user.displayName ||
      this.color() !== (p.user.color ?? 0) ||
      this.avatar() !== initialAvatar(p.user) ||
      (this.avatar() === 'photo' && this.pendingPhoto() !== null) ||
      this.genreId() !== (p.favoriteGenre?.id ?? null) ||
      this.favorite()?.id !== p.favoriteTitle?.id
    );
  });

  constructor() {
    // o resolver da rota já trouxe o perfil (ver features/profile/profile-resolver.ts); sem ele, busca aqui
    const resolved = this.route.snapshot.data['savedProfile'] as Profile | null | undefined;
    if (resolved) this.reset(resolved);
    else this.load();
  }

  protected load(): void {
    this.loadError.set(false);
    this.api
      .profile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (p) => this.reset(p), error: () => this.loadError.set(true) });
  }

  /** Volta o rascunho para o que está salvo. */
  protected reset(p = this.profile()): void {
    if (!p) return;
    this.profile.set(p);
    this.name.set(p.user.displayName);
    this.color.set(p.user.color ?? 0);
    this.avatar.set(initialAvatar(p.user));
    this.photo.set(p.user.photoUrl ?? null);
    this.pendingPhoto.set(null);
    this.genreId.set(p.favoriteGenre?.id ?? null);
    this.favorite.set(p.favoriteTitle);
    this.photoError.set(null);
  }

  protected setGenre(value: string): void {
    this.genreId.set(value ? Number(value) : null);
  }

  // --- foto ---

  protected async pickPhoto(input: HTMLInputElement): Promise<void> {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.photoError.set(null);
    try {
      const prepared = await preparePhoto(file);
      this.photo.set(prepared.preview);
      this.pendingPhoto.set(prepared.blob);
      this.avatar.set('photo');
    } catch (e) {
      this.photoError.set(e instanceof PhotoError ? e.message : 'Não deu pra usar essa imagem.');
    }
  }

  // --- salvar / sair ---

  protected save(event: Event): void {
    event.preventDefault();
    const p = this.profile();
    if (!p || this.saving() || !this.dirty() || this.nameError()) return;
    this.saving.set(true);
    this.status.set(null);
    this.api
      .updateProfile({
        displayName: this.cleanName(),
        color: this.color(),
        mascot: this.mascotToSave(),
        favoriteGenreId: this.genreId(),
        favoriteTitleId: this.favorite()?.id ?? null,
      })
      .pipe(
        switchMap((saved) => this.savePhoto(saved)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          this.auth.updateUser(saved.user);
          this.reset(saved);
          this.saving.set(false);
          this.status.set({ kind: 'ok', text: 'Perfil salvo.' });
        },
        error: (e: HttpErrorResponse) => {
          this.saving.set(false);
          const detail = typeof e.error?.detail === 'string' ? e.error.detail : null;
          this.status.set({
            kind: 'error',
            text: detail ?? 'Não deu pra salvar agora. Tenta de novo?',
          });
        },
      });
  }

  /** Depois do resto do perfil: envia a foto nova, ou apaga a salva se trocou por Bu/iniciais. */
  private savePhoto(saved: Profile): Observable<Profile> {
    const pending = this.pendingPhoto();
    if (this.avatar() === 'photo' && pending) return this.api.uploadPhoto(pending);
    if (this.avatar() !== 'photo' && saved.user.photoUrl) return this.api.removePhoto();
    return of(saved);
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/entrar']);
  }
}

function initialAvatar(user: UserRef): AvatarChoice {
  if (user.photoUrl) return 'photo';
  return user.mascot ?? 'initials';
}
