import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import {
  CursorPage,
  FriendsLiked,
  LoginResponse,
  MediaType,
  Profile,
  ProfileActivity,
  ProfileUpdate,
  Room,
  TitleCard,
  TitleLikes,
  UserRef,
} from './models';

/** Login, curtidas e salas de Match. O token entra sozinho pelo authInterceptor. */
@Injectable({ providedIn: 'root' })
export class SocialApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/auth/login`, { username, password });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, null);
  }

  me(): Observable<UserRef> {
    return this.http.get<UserRef>(`${this.base}/auth/me`);
  }

  // --- perfil ---

  profile(): Observable<Profile> {
    return this.http.get<Profile>(`${this.base}/me/profile`);
  }

  profileActivity(): Observable<ProfileActivity> {
    return this.http.get<ProfileActivity>(`${this.base}/me/profile/activity`);
  }

  updateProfile(changes: ProfileUpdate): Observable<Profile> {
    return this.http.put<Profile>(`${this.base}/me/profile`, changes);
  }

  // --- curtidas ---

  like(titleId: number): Observable<void> {
    return this.http.put<void>(`${this.base}/likes/${titleId}`, null);
  }

  unlike(titleId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/likes/${titleId}`);
  }

  likes(titleIds: number[]): Observable<TitleLikes[]> {
    return this.http.get<TitleLikes[]>(`${this.base}/likes`, {
      params: new HttpParams().set('titleIds', titleIds.join(',')),
    });
  }

  friendsLiked(size = 15): Observable<FriendsLiked[]> {
    return this.http.get<FriendsLiked[]>(`${this.base}/likes/friends`, { params: { size } });
  }

  // --- Match ---

  createRoom(filters: { mediaType: MediaType | null; genre: string | null }): Observable<Room> {
    return this.http.post<Room>(`${this.base}/match/rooms`, filters);
  }

  /** Salas esperando gente (de todo mundo) e as que eu estou votando. */
  openRooms(): Observable<Room[]> {
    return this.http.get<Room[]>(`${this.base}/match/rooms`);
  }

  /** Só o dono, com pelo menos 2 pessoas na sala. */
  startRoom(code: string): Observable<Room> {
    return this.http.post<Room>(`${this.base}/match/rooms/${encodeURIComponent(code)}/start`, null);
  }

  /** Só o dono, enquanto a sala espera. */
  closeRoom(code: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/match/rooms/${encodeURIComponent(code)}`);
  }

  room(code: string): Observable<Room> {
    return this.http.get<Room>(`${this.base}/match/rooms/${encodeURIComponent(code)}`);
  }

  joinRoom(code: string): Observable<Room> {
    return this.http.post<Room>(`${this.base}/match/rooms/${encodeURIComponent(code)}/join`, null);
  }

  leaveRoom(code: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/match/rooms/${encodeURIComponent(code)}/participants/me`);
  }

  roomDeck(code: string, cursor: string | null | undefined, size = 20): Observable<CursorPage<TitleCard>> {
    let params = new HttpParams().set('size', size);
    if (cursor) params = params.set('cursor', cursor);
    return this.http.get<CursorPage<TitleCard>>(`${this.base}/match/rooms/${encodeURIComponent(code)}/deck`, { params });
  }

  vote(code: string, titleId: number, liked: boolean): Observable<Room> {
    return this.http.put<Room>(`${this.base}/match/rooms/${encodeURIComponent(code)}/votes/${titleId}`, { liked });
  }

  unvote(code: string, titleId: number): Observable<Room> {
    return this.http.delete<Room>(`${this.base}/match/rooms/${encodeURIComponent(code)}/votes/${titleId}`);
  }
}
