import { HttpErrorResponse } from '@angular/common/http';

/**
 * The backend reports errors as RFC 9457 problem details. Domain rule violations carry a readable `detail`.
 */
export function problemMessage(error: unknown, fallback: string): string {
  if (
    error instanceof HttpErrorResponse &&
    typeof error.error?.detail === 'string' &&
    error.status < 500
  ) {
    return error.error.detail;
  }
  return fallback;
}
