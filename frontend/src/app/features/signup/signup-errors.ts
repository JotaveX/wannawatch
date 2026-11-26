import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from '../../core/api/models';

/** A mensagem em português que o back manda nos erros 4xx (ProblemDetail), se houver. */
export function problemDetail(e: HttpErrorResponse): string | null {
  if (e.status < 400 || e.status >= 500) return null;
  const detail = (e.error as ApiProblem | null)?.detail;
  return typeof detail === 'string' && detail ? detail : null;
}
