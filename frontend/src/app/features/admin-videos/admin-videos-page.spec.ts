import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../core/api/api-config';
import { TitleVideo, TitleVideoSearch } from '../../core/api/models';
import { AdminVideosPage } from './admin-videos-page';

function video(id: number, over: Partial<TitleVideo> = {}): TitleVideo {
  return {
    id,
    externalKey: `abcdefghij${id}`.slice(-11),
    videoTitle: `Vídeo ${id}`,
    channelTitle: 'Canal',
    publishedAt: null,
    durationSeconds: 30,
    width: 720,
    height: 1280,
    vertical: true,
    embeddable: true,
    blockedInRegion: false,
    searchRank: id,
    status: 'PENDING',
    reviewedAt: null,
    ...over,
  };
}

const searches: TitleVideoSearch[] = [
  {
    titleId: 1,
    title: 'Duna',
    mediaType: 'MOVIE',
    year: 2021,
    poster: null,
    query: 'Duna trailer #shorts',
    resultsCount: 3,
    searchedAt: '2026-11-20T10:00:00Z',
    videos: [
      video(1),
      video(2),
      video(3, { status: 'APPROVED', reviewedAt: '2026-11-21T10:00:00Z' }),
    ],
  },
  {
    titleId: 2,
    title: 'Fallout',
    mediaType: 'SERIES',
    year: 2024,
    poster: null,
    query: 'Fallout trailer #shorts',
    resultsCount: 1,
    searchedAt: '2026-11-20T10:00:00Z',
    videos: [video(4, { status: 'REJECTED', reviewedAt: '2026-11-22T10:00:00Z' })],
  },
];

describe('AdminVideosPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render() {
    const fixture = TestBed.createComponent(AdminVideosPage);
    http.expectOne('/api/admin/videos').flush(searches);
    await fixture.whenStable();
    return { fixture, page: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('separa a fila dos pendentes do histórico', async () => {
    const { page, el } = await render();
    expect(page.queue().map((g) => g.title)).toEqual(['Duna']);
    expect(page.queue()[0].pending.map((v) => v.id)).toEqual([1, 2]);
    // histórico: mais recente primeiro
    expect(page.history().map((h) => h.video.id)).toEqual([4, 3]);
    const tabs = [...el.querySelectorAll('[role="tab"]')].map((t) => t.textContent?.trim());
    expect(tabs).toEqual(['Verificação 2', 'Histórico 2']);
  });

  it('filtra o histórico por decisão e por texto', async () => {
    const { page } = await render();
    page.filter.set('APPROVED');
    expect(page.history().map((h) => h.video.id)).toEqual([3]);
    page.filter.set('ALL');
    page.query.set('fallout');
    expect(page.history().map((h) => h.video.id)).toEqual([4]);
  });

  it('aprova, tira da fila e desfaz', async () => {
    const { page } = await render();
    page.decide(page.queue()[0].pending[0], 'Duna', 'APPROVED');
    http.expectOne({ method: 'PATCH', url: '/api/admin/videos/1' }).flush(null);

    expect(page.queue()[0].pending.map((v) => v.id)).toEqual([2]);
    expect(page.history()[0].video.id).toBe(1);
    expect(page.toast()?.text).toBe('Aprovado: Duna');

    page.undo(page.toast()!);
    const req = http.expectOne({ method: 'PATCH', url: '/api/admin/videos/1' });
    expect(req.request.body).toEqual({ status: 'PENDING' });
    req.flush(null);
    expect(page.queue()[0].pending.map((v) => v.id)).toEqual([1, 2]);
    expect(page.toast()).toBeNull();
  });

  it('volta o status se o back recusar', async () => {
    const { page } = await render();
    page.decide(page.queue()[0].pending[0], 'Duna', 'REJECTED');
    http
      .expectOne('/api/admin/videos/1')
      .flush({ detail: 'Vídeo não encontrado.' }, { status: 404, statusText: 'Not Found' });
    expect(page.queue()[0].pending.map((v) => v.id)).toEqual([1, 2]);
    expect(page.message()).toBe('Vídeo não encontrado.');
  });

  it('reprova todos os pendentes de um título', async () => {
    const { page } = await render();
    page.rejectAll(page.queue()[0]);
    http.match((r) => r.method === 'PATCH').forEach((r) => r.flush(null));
    expect(page.queue()).toEqual([]);
    expect(page.toast()?.text).toBe('2 vídeos reprovados: Duna');
  });
});
