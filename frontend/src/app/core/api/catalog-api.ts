import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import {
  CursorPage,
  Genre,
  HomeResponse,
  MediaType,
  Page,
  SearchHit,
  TitleCard,
  TitleDetail,
  TitleSort,
} from './models';

export interface TitleFilters {
  type?: MediaType;
  /** slug do gênero, ex.: "ficcao-cientifica" */
  genre?: string;
}

/** Único ponto de contato do front com o back-end para o catálogo. */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  genres(type?: MediaType): Observable<Genre[]> {
    return this.http.get<Genre[]>(`${this.base}/genres`, { params: params({ type }) });
  }

  titles(
    query: TitleFilters & { sort?: TitleSort; page?: number; size?: number } = {},
  ): Observable<Page<TitleCard>> {
    return this.http.get<Page<TitleCard>>(`${this.base}/titles`, { params: params(query) });
  }

  title(id: number): Observable<TitleDetail> {
    return this.http.get<TitleDetail>(`${this.base}/titles/${id}`);
  }

  home(): Observable<HomeResponse> {
    return this.http.get<HomeResponse>(`${this.base}/home`);
  }

  swipeDeck(
    query: TitleFilters & { seed: string; cursor?: string | null; size?: number },
  ): Observable<CursorPage<TitleCard>> {
    return this.http.get<CursorPage<TitleCard>>(`${this.base}/swipe/deck`, { params: params(query) });
  }

  reels(query: TitleFilters & { cursor?: string | null; size?: number } = {}): Observable<CursorPage<TitleCard>> {
    return this.http.get<CursorPage<TitleCard>>(`${this.base}/reels`, { params: params(query) });
  }

  search(q: string, page = 0, size = 20): Observable<Page<SearchHit>> {
    return this.http.get<Page<SearchHit>>(`${this.base}/search`, { params: params({ q, page, size }) });
  }
}

/** Monta os query params ignorando valores vazios. */
function params(values: object): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      p = p.set(key, String(value));
    }
  }
  return p;
}
