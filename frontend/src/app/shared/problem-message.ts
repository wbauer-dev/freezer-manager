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

/** The message code of a problem the backend reported with one (property `code`), e.g. `catalog.product.changed`. */
export function problemCode(error: unknown): string | null {
  return error instanceof HttpErrorResponse && typeof error.error?.code === 'string'
    ? error.error.code
    : null;
}

/**
 * A request for a file (responseType 'blob', e.g. a PDF) gets its problem details as a Blob, too. This reads them, so
 * that problemMessage() finds the detail; any other error is returned as it is.
 */
export async function readProblem(error: unknown): Promise<unknown> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
    return error;
  }
  try {
    return new HttpErrorResponse({
      error: JSON.parse(await error.error.text()),
      headers: error.headers,
      status: error.status,
      statusText: error.statusText,
      url: error.url ?? undefined,
    });
  } catch {
    return error;
  }
}
