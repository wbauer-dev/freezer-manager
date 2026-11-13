import { HttpInterceptorFn } from '@angular/common/http';
import { LOCALE_ID, inject } from '@angular/core';

/**
 * The backend answers in the user's language: error messages, sticker texts and, for a household's first visit,
 * the starter catalog.
 */
export const acceptLanguageInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'Accept-Language': inject(LOCALE_ID) } }));
};
