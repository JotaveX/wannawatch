import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { DiscoverRequest, DiscoverResult, TitleVideoSearch, VideoStatus } from './models';

/** Endpoints de admin dos vídeos do Reels (o token de login entra pelo authInterceptor; só admin). */
@Injectable({ providedIn: 'root' })
export class VideoAdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  /** Busca vídeos para `limit` títulos escolhidos pelos filtros (1 título = 1 busca da cota). */
  discover(request: DiscoverRequest): Observable<DiscoverResult> {
    return this.http.post<DiscoverResult>(`${this.base}/admin/videos/discover`, request);
  }

  list(): Observable<TitleVideoSearch[]> {
    return this.http.get<TitleVideoSearch[]>(`${this.base}/admin/videos`);
  }

  setStatus(id: number, status: VideoStatus): Observable<void> {
    return this.http.patch<void>(`${this.base}/admin/videos/${id}`, { status });
  }
}
