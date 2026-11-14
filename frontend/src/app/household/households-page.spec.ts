import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';

import { HouseholdsPage } from './households-page';
import { HouseholdSummary } from './household.model';

describe('HouseholdsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: OidcSecurityService,
          useValue: {
            userData: signal({ userData: { given_name: 'Alice', name: 'Alice Example' } }),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(households: HouseholdSummary[]) {
    const fixture = TestBed.createComponent(HouseholdsPage);
    TestBed.tick();
    http.expectOne('/api/households').flush(households);
    await fixture.whenStable();
    return fixture;
  }

  function settle(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve));
  }

  it('invites to found the first household when there is none', async () => {
    const fixture = await render([]);

    expect(fixture.nativeElement.textContent).toContain('Found your first household');
  });

  it('greets the user by first name', async () => {
    const fixture = await render([]);

    expect(fixture.nativeElement.querySelector('h1').textContent).toMatch(/Good \w+, Alice/);
  });

  it('lists households with the role of the current user', async () => {
    const fixture = await render([
      { id: '1', name: 'Miller family', myRole: 'OWNER', memberCount: 2 },
    ]);

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Miller family');
    expect(text).toContain('2 members · you are owner');
    expect(text).toContain('Found another household');
  });

  it('founds a household and opens it', async () => {
    const fixture = await render([]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = 'Miller family';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await settle();

    const request = http.expectOne({ method: 'POST', url: '/api/households' });
    expect(request.request.body).toEqual({ name: 'Miller family' });
    request.flush({
      id: '42',
      name: 'Miller family',
      foundedAt: '2026-11-13T10:00:00Z',
      members: [],
    });
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/households', '42']);
  });

  it('does not submit without a name', async () => {
    const fixture = await render([]);

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await settle();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Please enter a name.');
  });

  it('does not submit a name of only spaces', async () => {
    const fixture = await render([]);

    await submitName(fixture, '   ');

    expect(fixture.nativeElement.textContent).toContain('Please enter a name.');
    // afterEach: no request was sent
  });

  it('sends the name without surrounding spaces', async () => {
    const fixture = await render([]);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    await submitName(fixture, '  Miller family ');

    const request = http.expectOne({ method: 'POST', url: '/api/households' });
    expect(request.request.body).toEqual({ name: 'Miller family' });
    request.flush({ id: '42', name: 'Miller family', foundedAt: '', myRole: 'OWNER', members: [] });
    await settle();
  });

  it('shows why the backend rejected the household', async () => {
    const fixture = await render([]);

    await submitName(fixture, 'Miller family');
    http
      .expectOne({ method: 'POST', url: '/api/households' })
      .flush(
        { detail: 'A household name has at most 50 characters.' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'A household name has at most 50 characters.',
    );
  });

  async function submitName(fixture: ComponentFixture<HouseholdsPage>, name: string) {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = name;
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await settle();
    await fixture.whenStable();
  }
});
