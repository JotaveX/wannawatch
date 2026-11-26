import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { DiscoverResult, TitleVideoSearch, VideoStatus } from './models';

/** Endpoints de admin dos vídeos do Reels (X-Admin-Token). Só a página /dev/videos usa. */
@Injectable({ providedIn: 'root' })
export class VideoAdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  /** Busca vídeos para os `limit` títulos mais populares ainda não buscados (1 busca = 1 da cota). */
  discover(token: string, limit: number): Observable<DiscoverResult> {
    return this.http.post<DiscoverResult>(
      `${this.base}/admin/videos/discover`,
      { limit },
      { headers: this.headers(token) },
    );
  }

  list(token: string): Observable<TitleVideoSearch[]> {
    return this.http.get<TitleVideoSearch[]>(`${this.base}/admin/videos`, {
      headers: this.headers(token),
    });
  }

  setStatus(token: string, id: number, status: VideoStatus): Observable<void> {
    return this.http.patch<void>(
      `${this.base}/admin/videos/${id}`,
      { status },
      { headers: this.headers(token) },
    );
  }

  private headers(token: string): HttpHeaders {
    return new HttpHeaders({ 'X-Admin-Token': token });
  }
}
