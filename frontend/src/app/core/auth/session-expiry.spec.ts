import { Component, WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { EventTypes, OidcSecurityService, PublicEventsService } from 'angular-auth-oidc-client';
import { Subject } from 'rxjs';

import { takeReturnUrl } from './auth-guard';
import { SessionExpiry, sessionExpiryInterceptor } from './session-expiry';

@Component({ template: '' })
class Page {}

describe('SessionExpiry', () => {
  let authorize: ReturnType<typeof vi.fn>;
  let authenticated: WritableSignal<{ isAuthenticated: boolean }>;
  let events: Subject<{ type: EventTypes; value: unknown }>;
  let http: HttpTestingController;
  let client: HttpClient;

  /** Starts the app — again, to simulate the page that comes back from the login. */
  async function start(url = '/households/h1/shopping'): Promise<void> {
    TestBed.resetTestingModule();
    authorize = vi.fn();
    authenticated = signal({ isAuthenticated: true });
    events = new Subject();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiryInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: Page }]),
        { provide: OidcSecurityService, useValue: { authorize, authenticated } },
        { provide: PublicEventsService, useValue: { registerForEvents: () => events } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    TestBed.inject(SessionExpiry);
    await navigate(url);
  }

  async function navigate(url: string): Promise<void> {
    await TestBed.inject(Router).navigateByUrl(url);
  }

  function call(url = '/api/households/h1/shopping-list'): void {
    client.get(url).subscribe({ error: () => undefined });
  }

  function rejectToken(url = '/api/households/h1/shopping-list'): void {
    http.expectOne(url).flush(null, { status: 401, statusText: 'Unauthorized' });
  }

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('logs in again when the API rejects the token, once, and comes back to the page', async () => {
    await start();
    call('/api/households/h1/shopping-list');
    call('/api/households/h1/products');

    rejectToken('/api/households/h1/shopping-list');
    rejectToken('/api/households/h1/products');

    expect(authorize).toHaveBeenCalledTimes(1);
    expect(takeReturnUrl()).toBe('/households/h1/shopping');
  });

  it('logs in again when the session could not be renewed', async () => {
    await start();

    authenticated.set({ isAuthenticated: false });
    events.next({ type: EventTypes.SilentRenewFailed, value: 'invalid_grant' });

    expect(authorize).toHaveBeenCalledTimes(1);
    expect(takeReturnUrl()).toBe('/households/h1/shopping');
  });

  it('keeps the login when renewing failed for a network error', async () => {
    await start();

    events.next({ type: EventTypes.SilentRenewFailed, value: 'offline' });

    expect(authorize).not.toHaveBeenCalled();
  });

  it('ignores other errors', async () => {
    await start();
    call();
    call('/i18n/messages.de.json');

    http
      .expectOne('/api/households/h1/shopping-list')
      .flush(null, { status: 500, statusText: 'Server Error' });
    http
      .expectOne('/i18n/messages.de.json')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authorize).not.toHaveBeenCalled();
  });

  it('tries once per navigation', async () => {
    await start();
    call();
    rejectToken();
    call();
    rejectToken();
    expect(authorize).toHaveBeenCalledTimes(1);

    await navigate('/households/h1/catalog');
    call();
    rejectToken();

    expect(authorize).toHaveBeenCalledTimes(2);
  });

  it('does not loop when the API rejects even the new token', async () => {
    await start();
    call();
    rejectToken();
    expect(authorize).toHaveBeenCalledTimes(1);

    // Back from the login, the API still says no: show the error instead of logging in again.
    await start();
    call();
    rejectToken();
    await navigate('/households/h1/catalog');
    call();
    rejectToken();
    expect(authorize).not.toHaveBeenCalled();

    // Once the API accepted a call, a later expiry logs in again.
    call();
    http.expectOne('/api/households/h1/shopping-list').flush({ entries: [] });
    call();
    rejectToken();
    expect(authorize).toHaveBeenCalledTimes(1);
  });
});
