import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';

import { HouseholdSummary } from './household.model';
import { startPageGuard } from './start-page-guard';

@Component({ template: '' })
class Page {}

describe('startPageGuard', () => {
  let http: HttpTestingController;
  let router: Router;

  function setUp(isAuthenticated = true): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', pathMatch: 'full', canActivate: [startPageGuard], component: Page },
          { path: 'households', component: Page },
          { path: 'households/:householdId', component: Page },
        ]),
        {
          provide: OidcSecurityService,
          useValue: { authenticated: signal({ isAuthenticated }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  }

  afterEach(() => http.verify());

  async function start(respond: (request: ReturnType<HttpTestingController['expectOne']>) => void) {
    const navigation = router.navigateByUrl('/');
    await new Promise((resolve) => setTimeout(resolve));
    respond(http.expectOne('/api/households'));
    await navigation;
    return router.url;
  }

  function household(id: string): HouseholdSummary {
    return { id, name: `Household ${id}`, myRole: 'MEMBER', memberCount: 2 };
  }

  it('goes straight into the only household', async () => {
    setUp();

    const url = await start((request) => request.flush([household('h1')]));

    expect(url).toBe('/households/h1');
  });

  it('lets users with several households pick one', async () => {
    setUp();

    const url = await start((request) => request.flush([household('h1'), household('h2')]));

    expect(url).toBe('/households');
  });

  it('invites users without a household to found one', async () => {
    setUp();

    const url = await start((request) => request.flush([]));

    expect(url).toBe('/households');
  });

  it('shows the households page when they cannot be loaded', async () => {
    setUp();

    const url = await start((request) =>
      request.flush(null, { status: 500, statusText: 'Server Error' }),
    );

    expect(url).toBe('/households');
  });

  it('leaves users without a session to the login', async () => {
    setUp(false);

    expect(await TestBed.runInInjectionContext(() => startPageGuard(null!, null!))).toBe(true);
    // afterEach: no request was sent
  });
});
