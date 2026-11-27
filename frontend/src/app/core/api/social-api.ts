import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import {
  AccountResponse,
  AccountStatus,
  CursorPage,
  FriendsLiked,
  InteractionSource,
  LoginResponse,
  MediaType,
  Profile,
  ProfileActivity,
  ProfileUpdate,
  RegisterRequest,
  Room,
  SignalKind,
  TitleCard,
  TitleLikes,
  TitleSignals,
  UserRef,
} from './models';

/** Login, cadastro, curtidas e salas de Match. O token entra sozinho pelo authInterceptor. */
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

  /** Quem está logado e em que etapa do cadastro está. */
  account(): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`${this.base}/auth/account`);
  }

  // --- cadastro ---

  /** Cria a conta (já logada) e manda o código de confirmação por e-mail. */
  register(body: RegisterRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/auth/register`, body);
  }

  /** Etapa 2: 5 a 50 títulos (viram "quero ver") e o favorito, que precisa estar entre eles. */
  pickTitles(titleIds: number[], favoriteTitleId: number | null): Observable<AccountStatus> {
    return this.http.put<AccountStatus>(`${this.base}/me/onboarding/titles`, {
      titleIds,
      favoriteTitleId,
    });
  }

  verifyEmail(code: string): Observable<AccountStatus> {
    return this.http.post<AccountStatus>(`${this.base}/auth/email/verify`, { code });
  }

  /** No máximo um por minuto (400 antes disso). */
  resendEmail(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/email/resend`, null);
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

  /** Envia a foto de perfil (o back recorta em 192×192 e devolve o perfil com a `photoUrl`). */
  uploadPhoto(photo: Blob): Observable<Profile> {
    const form = new FormData();
    form.append('file', photo, 'foto.jpg');
    return this.http.put<Profile>(`${this.base}/me/profile/photo`, form);
  }

  removePhoto(): Observable<Profile> {
    return this.http.delete<Profile>(`${this.base}/me/profile/photo`);
  }

  // --- curtidas ---

  like(titleId: number, source?: InteractionSource): Observable<void> {
    return this.http.put<void>(`${this.base}/likes/${titleId}`, null, {
      params: sourceParam(source),
    });
  }

  unlike(titleId: number, source?: InteractionSource): Observable<void> {
    return this.http.delete<void>(`${this.base}/likes/${titleId}`, { params: sourceParam(source) });
  }

  likes(titleIds: number[]): Observable<TitleLikes[]> {
    return this.http.get<TitleLikes[]>(`${this.base}/likes`, {
      params: new HttpParams().set('titleIds', titleIds.join(',')),
    });
  }

  friendsLiked(size = 15): Observable<FriendsLiked[]> {
    return this.http.get<FriendsLiked[]>(`${this.base}/likes/friends`, { params: { size } });
  }

  // --- passa / talvez / já vi / salvar ---

  putSignal(titleId: number, kind: SignalKind, source?: InteractionSource): Observable<void> {
    return this.http.put<void>(`${this.base}/titles/${titleId}/signals/${kind}`, null, {
      params: sourceParam(source),
    });
  }

  deleteSignal(titleId: number, kind: SignalKind, source?: InteractionSource): Observable<void> {
    return this.http.delete<void>(`${this.base}/titles/${titleId}/signals/${kind}`, {
      params: sourceParam(source),
    });
  }

  signals(titleIds: number[]): Observable<TitleSignals[]> {
    return this.http.get<TitleSignals[]>(`${this.base}/me/signals`, {
      params: new HttpParams().set('ids', titleIds.join(',')),
    });
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
    return this.http.delete<void>(
      `${this.base}/match/rooms/${encodeURIComponent(code)}/participants/me`,
    );
  }

  roomDeck(
    code: string,
    cursor: string | null | undefined,
    size = 20,
  ): Observable<CursorPage<TitleCard>> {
    let params = new HttpParams().set('size', size);
    if (cursor) params = params.set('cursor', cursor);
    return this.http.get<CursorPage<TitleCard>>(
      `${this.base}/match/rooms/${encodeURIComponent(code)}/deck`,
      { params },
    );
  }

  vote(code: string, titleId: number, liked: boolean): Observable<Room> {
    return this.http.put<Room>(
      `${this.base}/match/rooms/${encodeURIComponent(code)}/votes/${titleId}`,
      { liked },
    );
  }

  unvote(code: string, titleId: number): Observable<Room> {
    return this.http.delete<Room>(
      `${this.base}/match/rooms/${encodeURIComponent(code)}/votes/${titleId}`,
    );
  }
}

function sourceParam(source: InteractionSource | undefined): HttpParams {
  return source ? new HttpParams().set('source', source) : new HttpParams();
}
