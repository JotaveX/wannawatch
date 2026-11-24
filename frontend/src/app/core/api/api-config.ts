import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/** Raiz da API do back-end, ex.: http://localhost:8081/api */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});
