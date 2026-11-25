import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Profile, ProfileActivity } from '../../core/api/models';
import { SocialApi } from '../../core/api/social-api';
import { userColors } from '../../core/social/user-colors';
import { titleLink } from '../../core/title-link';
import { Avatar } from '../../shared/ui/avatar';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { buMascot } from '../../shared/ui/mascots';
import { Poster } from '../../shared/ui/poster';

/**
 * Página de perfil (só leitura): quem é você no wannawatch?, seus números, curtidas por semana
 * e os últimos curtidos. Para editar, "Editar perfil" leva aos Ajustes.
 */
@Component({
  selector: 'ww-profile-page',
  imports: [RouterLink, Avatar, Button, Icon, Poster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.css',
})
export class ProfilePage {
  private readonly api = inject(SocialApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly titleLink = titleLink;
  /** Bu do estado vazio dos curtidos */
  protected readonly emptyBu = buMascot(7);
  /** no celular o avatar encolhe para o nome caber ao lado */
  protected readonly avatarSize = globalThis.matchMedia?.('(max-width: 479px)').matches ? 88 : 120;

  protected readonly profile = signal<Profile | null>(null);
  protected readonly loadError = signal(false);
  protected readonly activity = signal<ProfileActivity | null>(null);
  protected readonly activityError = signal(false);

  protected readonly since = computed(() => {
    const p = this.profile();
    return p ? shortDate(p.memberSince) : null;
  });

  /** Cor da pessoa: pinta as semanas com curtidas. */
  protected readonly accent = computed(() => {
    const p = this.profile();
    return p ? userColors(p.user).bg : null;
  });

  /** 12 semanas; enquanto carrega, 12 vazias. `level` vai de 0 (nada) a 4 (a semana mais cheia). */
  protected readonly weeks = computed(() => {
    const counts = this.activity()?.weeklyLikes ?? Array<number>(12).fill(0);
    const max = Math.max(1, ...counts);
    return counts.map((count, i) => ({
      count,
      level: count === 0 ? 0 : Math.max(1, Math.ceil((count / max) * 4)),
      label: weekLabel(counts.length - 1 - i, count),
    }));
  });
  protected readonly likesInWeeks = computed(() => this.weeks().reduce((sum, w) => sum + w.count, 0));

  constructor() {
    const resolved = this.route.snapshot.data['savedProfile'] as Profile | null | undefined;
    if (resolved) this.profile.set(resolved);
    else this.load();
    this.loadActivity();
  }

  protected load(): void {
    this.loadError.set(false);
    this.api
      .profile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (p) => this.profile.set(p), error: () => this.loadError.set(true) });
  }

  protected loadActivity(): void {
    this.activityError.set(false);
    this.api
      .profileActivity()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (a) => this.activity.set(a), error: () => this.activityError.set(true) });
  }
}

/** "2026-11-25T…" → "25 de nov" (com o ano, se não for este). */
function shortDate(iso: string): string | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date
    .toLocaleDateString('pt-BR', { day: 'numeric', month: 'short', ...(sameYear ? {} : { year: 'numeric' }) })
    .replace(/\./g, '');
}

function weekLabel(ago: number, count: number): string {
  const when = ago === 0 ? 'Esta semana' : ago === 1 ? 'Semana passada' : `${ago} semanas atrás`;
  const what = count === 0 ? 'nenhuma curtida' : count === 1 ? '1 curtida' : `${count} curtidas`;
  return `${when}: ${what}`;
}
