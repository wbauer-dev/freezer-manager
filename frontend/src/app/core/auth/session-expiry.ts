import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { EventTypes, OidcSecurityService, PublicEventsService } from 'angular-auth-oidc-client';
import { filter, tap } from 'rxjs';

import { rememberReturnUrl } from './auth-guard';

/** Set while a new login is under way, so the page that comes back from it knows. */
const LOGGING_IN_AGAIN = 'freezer.loggingInAgain';

/**
 * Logs in again when the session has ended — for example after a phone tab slept longer than Keycloak's idle
 * timeout. Then the token can no longer be renewed: the library forgets the login, and the API answers 401. Users
 * get the login page and come back to the page they were on.
 *
 * At most one login per navigation. A page that comes back from such a login does not start another one before the
 * API has accepted a call: if it rejects even fresh tokens, its errors are shown instead of logging in forever.
 */
@Injectable({ providedIn: 'root' })
export class SessionExpiry {
  private readonly auth = inject(OidcSecurityService);
  private readonly router = inject(Router);

  /** A login was started during the current navigation. */
  private started = false;
  /** This page came back from a login started here, and the API has not accepted a call since. */
  private unconfirmed = takeFlag();

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => (this.started = false));
    inject(PublicEventsService)
      .registerForEvents()
      .pipe(
        filter((event) => event.type === EventTypes.SilentRenewFailed),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        // The library keeps the login after a network error; a rejected refresh token ends it.
        if (!this.auth.authenticated().isAuthenticated) {
          this.loginAgain();
        }
      });
  }

  /** The session has ended: remembers the current page and goes to the login. */
  loginAgain(): void {
    if (this.started || this.unconfirmed) {
      return;
    }
    this.started = true;
    rememberReturnUrl(this.router.url);
    setFlag();
    this.auth.authorize();
  }

  /** The API accepted a call, so the session works. */
  confirm(): void {
    this.unconfirmed = false;
  }
}

/** Starts a new login when the API rejects the token (see SessionExpiry). */
export const sessionExpiryInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const session = inject(SessionExpiry);
  return next(request).pipe(
    tap({
      next: (event) => {
        if (event.type === HttpEventType.Response) {
          session.confirm();
        }
      },
      error: (error) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          session.loginAgain();
        }
      },
    }),
  );
};

function setFlag(): void {
  try {
    sessionStorage.setItem(LOGGING_IN_AGAIN, 'true');
  } catch {
    // Without storage, a backend that rejects every token could send users to the login more than once.
  }
}

function takeFlag(): boolean {
  try {
    const flag = sessionStorage.getItem(LOGGING_IN_AGAIN) !== null;
    sessionStorage.removeItem(LOGGING_IN_AGAIN);
    return flag;
  } catch {
    return false;
  }
}
