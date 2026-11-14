import { Component, WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';

import { AuthCallback } from './auth-callback';
import { authGuard, rememberReturnUrl } from './auth-guard';

@Component({ template: '' })
class Page {}

describe('authGuard and AuthCallback', () => {
  let authorize: ReturnType<typeof vi.fn>;
  let authenticated: WritableSignal<{ isAuthenticated: boolean }>;
  let router: Router;
  let harness: RouterTestingHarness;

  /** Opens the URL like the browser would, and waits until the app has settled (incl. the callback's navigation). */
  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
  }

  beforeEach(async () => {
    authorize = vi.fn();
    authenticated = signal({ isAuthenticated: false });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'auth/callback', component: AuthCallback },
          { path: '', pathMatch: 'full', component: Page },
          { path: 's/:code', canActivate: [authGuard], component: Page },
        ]),
        { provide: OidcSecurityService, useValue: { authorize, authenticated } },
      ],
    });
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => sessionStorage.clear());

  it('lets members in without touching the login', async () => {
    authenticated.set({ isAuthenticated: true });

    await open('/s/K7Q2M9XA');

    expect(router.url).toBe('/s/K7Q2M9XA');
    expect(authorize).not.toHaveBeenCalled();
    expect(sessionStorage.length).toBe(0);
  });

  it('sends visitors to the login and brings them back to where they wanted to go', async () => {
    await open('/s/K7Q2M9XA');

    expect(authorize).toHaveBeenCalledTimes(1);
    expect(router.url).not.toBe('/s/K7Q2M9XA');

    // Back from the identity provider, logged in.
    authenticated.set({ isAuthenticated: true });
    await open('/auth/callback');

    expect(router.url).toBe('/s/K7Q2M9XA');
  });

  it('uses a remembered address only once', async () => {
    rememberReturnUrl('/s/K7Q2M9XA');
    authenticated.set({ isAuthenticated: true });

    await open('/auth/callback');
    expect(router.url).toBe('/s/K7Q2M9XA');

    // A later login that started without remembering anything ends on the start page, not on the old address.
    await open('/auth/callback');
    expect(router.url).toBe('/');
  });

  it('keeps no address of its own in the library, so the library never navigates past the callback page', async () => {
    await open('/s/K7Q2M9XA');

    // Only the app's key is written; the library's saved route ("redirect") stays empty.
    expect(Object.keys(sessionStorage)).toEqual(['freezer.returnUrl']);
  });
});
