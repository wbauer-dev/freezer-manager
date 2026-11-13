import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { OidcSecurityService, autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';

const RETURN_URL = 'freezer.returnUrl';

/**
 * Sends users who are not logged in to the identity provider — and remembers where they wanted to go (e.g. a scanned
 * label), so the callback page can take them there afterwards.
 */
export const authGuard: CanActivateFn = (route, state) => {
  if (!inject(OidcSecurityService).authenticated().isAuthenticated) {
    try {
      sessionStorage.setItem(RETURN_URL, state.url);
    } catch {
      // Without storage, users land on the start page after logging in.
    }
  }
  return autoLoginPartialRoutesGuard(route, state);
};

/** The address remembered before logging in; read only once. */
export function takeReturnUrl(): string | null {
  try {
    const url = sessionStorage.getItem(RETURN_URL);
    sessionStorage.removeItem(RETURN_URL);
    return url;
  } catch {
    return null;
  }
}
