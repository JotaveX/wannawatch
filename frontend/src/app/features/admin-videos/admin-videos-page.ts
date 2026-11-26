import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChildren,
} from '@angular/core';
import { DiscoverRequest, TitleVideo, TitleVideoSearch, VideoStatus } from '../../core/api/models';
import { VideoAdminApi } from '../../core/api/video-admin-api';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { Poster } from '../../shared/ui/poster';
import { SegmentOption, Segmented } from '../../shared/ui/segmented';
import { DiscoverForm } from './discover-form';
import { UndoToast } from './undo-toast';
import { VideoCard } from './video-card';

type Tab = 'review' | 'history';
type HistoryFilter = 'ALL' | 'APPROVED' | 'REJECTED';

/** Um título com os vídeos que ainda esperam revisão. */
interface QueueGroup extends TitleVideoSearch {
  pending: TitleVideo[];
}

interface HistoryEntry {
  title: string;
  video: TitleVideo;
}

/** Aviso de baixo com "Desfazer": volta os vídeos para o status (e a data) de antes. */
interface Toast {
  text: string;
  previous: { id: number; status: VideoStatus; reviewedAt: string | null }[];
}

const TOAST_MS = 6000;

/**
 * Revisão dos vídeos achados na API do YouTube (/admin/videos, só para administradores).
 * Duas abas: Verificação (fila dos pendentes, agrupada por título, e a busca de mais vídeos) e
 * Histórico (tudo que já foi aprovado ou reprovado, mais recente primeiro).
 */
@Component({
  selector: 'ww-admin-videos-page',
  imports: [Button, DiscoverForm, Icon, Poster, Segmented, UndoToast, VideoCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-videos-page.html',
  styleUrl: './admin-videos-page.css',
})
export class AdminVideosPage {
  private readonly api = inject(VideoAdminApi);
  private readonly tabButtons = viewChildren<ElementRef<HTMLButtonElement>>('tabBtn');
  private toastTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly tabs: { id: Tab; label: string }[] = [
    { id: 'review', label: 'Verificação' },
    { id: 'history', label: 'Histórico' },
  ];
  protected readonly filterOptions: SegmentOption<HistoryFilter>[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'APPROVED', label: 'Aprovados' },
    { value: 'REJECTED', label: 'Reprovados' },
  ];
  protected readonly skeletons = [0, 1, 2, 3, 4, 5];

  readonly tab = signal<Tab>('review');
  readonly filter = signal<HistoryFilter>('ALL');
  readonly query = signal('');
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly searches = signal<TitleVideoSearch[]>([]);
  readonly toast = signal<Toast | null>(null);

  readonly queue = computed<QueueGroup[]>(() =>
    this.searches()
      .map((s) => ({ ...s, pending: s.videos.filter((v) => v.status === 'PENDING') }))
      .filter((g) => g.pending.length > 0),
  );

  readonly history = computed<HistoryEntry[]>(() => {
    const filter = this.filter();
    const q = normalize(this.query().trim());
    return this.searches()
      .flatMap((s) => s.videos.map((video) => ({ title: s.title, video })))
      .filter(({ title, video }) => {
        if (video.status === 'PENDING') return false;
        if (filter !== 'ALL' && video.status !== filter) return false;
        if (!q) return true;
        return [title, video.videoTitle, video.channelTitle].some((t) =>
          normalize(t ?? '').includes(q),
        );
      })
      .sort((a, b) => (b.video.reviewedAt ?? '').localeCompare(a.video.reviewedAt ?? ''));
  });

  readonly stats = computed(() => {
    const all = this.searches().flatMap((s) => s.videos);
    const count = (status: VideoStatus) => all.filter((v) => v.status === status).length;
    const approved = count('APPROVED');
    const rejected = count('REJECTED');
    return {
      titles: this.searches().length,
      pending: count('PENDING'),
      approved,
      rejected,
      reviewed: approved + rejected,
    };
  });

  constructor() {
    this.load();
    inject(DestroyRef).onDestroy(() => clearTimeout(this.toastTimer));
  }

  load(): void {
    this.api.list().subscribe({
      next: (list) => {
        this.searches.set(list);
        this.loading.set(false);
        this.busy.set(false);
      },
      error: (e) => this.fail(e),
    });
  }

  discover(request: DiscoverRequest): void {
    this.busy.set(true);
    this.message.set('Buscando no YouTube…');
    this.api.discover(request).subscribe({
      next: (r) => {
        const done =
          !r.titlesSearched && !r.error
            ? 'Nenhum título encontrado com esses filtros.'
            : `${r.titlesSearched} título(s) buscado(s): ${r.videosFound} vertical(is) guardado(s), ` +
              `${r.horizontalSkipped} horizontal(is) descartado(s).`;
        this.message.set(r.error ? `${done} Parou: ${r.error}` : done);
        this.load();
      },
      error: (e) => this.fail(e),
    });
  }

  decide(video: TitleVideo, title: string, status: VideoStatus): void {
    const verb = { APPROVED: 'Aprovado', REJECTED: 'Reprovado', PENDING: 'De volta à fila' }[
      status
    ];
    this.apply([video], status, `${verb}: ${title}`);
  }

  rejectAll(group: QueueGroup): void {
    this.apply(
      group.pending,
      'REJECTED',
      `${group.pending.length} vídeos reprovados: ${group.title}`,
    );
  }

  undo(toast: Toast): void {
    this.hideToast();
    for (const p of toast.previous) {
      this.patch(p.id, p.status, p.reviewedAt);
      this.api.setStatus(p.id, p.status).subscribe({ error: (e) => this.fail(e) });
    }
  }

  /** Setas, Home e End trocam de aba (padrão de abas da WAI-ARIA). */
  onTabKey(event: KeyboardEvent): void {
    const i = this.tabs.findIndex((t) => t.id === this.tab());
    const last = this.tabs.length - 1;
    const next =
      event.key === 'ArrowRight'
        ? (i + 1) % this.tabs.length
        : event.key === 'ArrowLeft'
          ? (i - 1 + this.tabs.length) % this.tabs.length
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? last
              : null;
    if (next == null) return;
    event.preventDefault();
    this.tab.set(this.tabs[next].id);
    this.tabButtons()[next]?.nativeElement.focus();
  }

  /** Muda na tela na hora e manda para o back; se falhar, volta como estava. */
  private apply(videos: TitleVideo[], status: VideoStatus, text: string): void {
    const previous = videos.map((v) => ({ id: v.id, status: v.status, reviewedAt: v.reviewedAt }));
    const now = status === 'PENDING' ? null : new Date().toISOString();
    for (const v of videos) {
      this.patch(v.id, status, now);
      this.api.setStatus(v.id, status).subscribe({
        error: (e) => {
          this.patch(v.id, v.status, v.reviewedAt);
          this.fail(e);
        },
      });
    }
    this.showToast({ text, previous });
  }

  private patch(id: number, status: VideoStatus, reviewedAt: string | null): void {
    this.searches.update((list) =>
      list.map((s) =>
        s.videos.some((v) => v.id === id)
          ? { ...s, videos: s.videos.map((v) => (v.id === id ? { ...v, status, reviewedAt } : v)) }
          : s,
      ),
    );
  }

  private showToast(toast: Toast): void {
    clearTimeout(this.toastTimer);
    this.toast.set(toast);
    this.toastTimer = setTimeout(() => this.toast.set(null), TOAST_MS);
  }

  private hideToast(): void {
    clearTimeout(this.toastTimer);
    this.toast.set(null);
  }

  private fail(e: unknown): void {
    this.busy.set(false);
    this.loading.set(false);
    const status = e instanceof HttpErrorResponse ? e.status : 0;
    const detail = e instanceof HttpErrorResponse ? e.error?.detail : null;
    this.message.set(
      status === 403
        ? 'Só administradores podem ver esta página.'
        : (detail ?? 'Não deu para falar com o back-end.'),
    );
  }
}

function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
