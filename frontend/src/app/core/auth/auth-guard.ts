import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';

const RETURN_URL = 'freezer.returnUrl';

/**
 * Sends users who are not logged in to the identity provider — and remembers where they wanted to go (e.g. a scanned
 * label), so the callback page can take them there afterwards.
 *
 * Deliberately not the library's autoLoginPartialRoutesGuard: that one keeps a return address of its own and
 * navigates there while the login completes, before the callback page runs. With two mechanisms, the app's address
 * was left behind and sent a later login to an old page. Now every login start (here and in SessionExpiry) remembers
 * the address, and AuthCallback alone takes it.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(OidcSecurityService);
  // The login state is known here: the app initializer completes the auth check before the first navigation.
  if (auth.authenticated().isAuthenticated) {
    return true;
  }
  rememberReturnUrl(state.url);
  auth.authorize();
  return false;
};

/** Where to continue after logging in. */
export function rememberReturnUrl(url: string): void {
  try {
    sessionStorage.setItem(RETURN_URL, url);
  } catch {
    // Without storage, users land on the start page after logging in.
  }
}

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
