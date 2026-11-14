import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';

import { MembersPage } from './members-page';
import { HouseholdDetails, Invitation, Member, Role } from './household.model';

describe('MembersPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<MembersPage>;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const alice: Member = {
    userId: 'u-alice',
    displayName: 'Alice',
    role: 'OWNER',
    joinedAt: '2026-11-01T10:00:00Z',
    me: true,
  };
  const bob: Member = {
    userId: 'u-bob',
    displayName: 'Bob',
    role: 'MEMBER',
    joinedAt: '2026-11-02T10:00:00Z',
    me: false,
  };
  const invitation: Invitation = {
    code: 'q3J7d0lYzPq8n1Kx4bVt_w',
    createdAt: '2026-11-14T10:00:00Z',
    expiresAt: '2026-11-21T10:00:00Z',
  };

  function household(myRole: Role, members: Member[]): HouseholdDetails {
    return { id: 'h1', name: 'Miller family', foundedAt: '2026-11-01T10:00:00Z', myRole, members };
  }

  beforeEach(() => {
    snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function create(): void {
    fixture = TestBed.createComponent(MembersPage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
  }

  /** Opens the page as Alice, the owner, unless the household says otherwise. */
  async function open(
    details = household('OWNER', [alice, bob]),
    invitations: Invitation[] = [],
  ): Promise<void> {
    create();
    http.expectOne('/api/households/h1').flush(details);
    // Owners also load the open invitations, once the household says they are an owner.
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    if (details.myRole === 'OWNER') {
      http.expectOne('/api/households/h1/invitations').flush(invitations);
    }
    await fixture.whenStable();
  }

  function element<T extends HTMLElement>(selector: string): T {
    return fixture.nativeElement.querySelector(selector);
  }

  function all(selector: string): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll(selector));
  }

  function text(): string {
    return (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  async function click(selector: string): Promise<void> {
    element(selector).click();
    await fixture.whenStable();
  }

  /** Clicks a button that sends a request. The page is stable only once the request is answered. */
  function send(selector: string): void {
    element(selector).click();
    TestBed.tick();
  }

  it('offers to try again when the members could not be loaded', async () => {
    create();
    http.expectOne('/api/households/h1').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(text()).toContain('The members could not be loaded.');

    send('.load-error button');
    http.expectOne('/api/households/h1').flush(household('MEMBER', [{ ...alice, me: false }]));
    await fixture.whenStable();

    expect(text()).toContain('Alice');
    expect(text()).not.toContain('could not be loaded');
  });

  it('shows members without owner actions to a member', async () => {
    await open(
      household('MEMBER', [
        { ...alice, me: false },
        { ...bob, me: true },
      ]),
    );

    expect(text()).toContain('Bob (you)');
    expect(all('.role.owner')).toHaveLength(1);
    expect(all('.actions')).toHaveLength(0);
    expect(text()).not.toContain('Invite someone');
    expect(element('.leave')).toBeTruthy();
  });

  it('lets owners create an invitation link', async () => {
    await open();

    send('.create-invitation');
    http.expectOne({ method: 'POST', url: '/api/households/h1/invitations' }).flush(invitation);
    await fixture.whenStable();

    expect(element<HTMLInputElement>('.invitation.created .link').value).toBe(
      `${location.origin}/join/q3J7d0lYzPq8n1Kx4bVt_w`,
    );
    expect(text()).toContain('Open invitations');
    expect(text()).toContain('expires');
  });

  it('copies an invitation link', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await open(undefined, [invitation]);

    await click('.invitation .copy');

    expect(writeText).toHaveBeenCalledWith(`${location.origin}/join/${invitation.code}`);
    expect(snackBar.open).toHaveBeenCalledWith('Link copied', undefined, expect.anything());
  });

  it('lets owners revoke an invitation', async () => {
    await open(undefined, [invitation]);

    send('.invitation .revoke');
    http
      .expectOne({
        method: 'DELETE',
        url: `/api/households/h1/invitations/${invitation.code}`,
      })
      .flush(null);
    await fixture.whenStable();

    expect(all('.invitation')).toHaveLength(0);
  });

  it('lets owners remove a member after confirming', async () => {
    await open();

    await click('.remove');
    expect(text()).toContain('Remove Bob from the household?');
    send('.confirm-remove');
    http
      .expectOne({ method: 'DELETE', url: '/api/households/h1/members/u-bob' })
      .flush(household('OWNER', [alice]));
    await fixture.whenStable();

    expect(all('.member')).toHaveLength(1);
    expect(text()).not.toContain('Bob');
  });

  it('lets owners make a member an owner after confirming', async () => {
    await open();

    await click('.make-owner');
    send('.confirm-make-owner');
    http
      .expectOne({ method: 'POST', url: '/api/households/h1/members/u-bob/make-owner' })
      .flush(household('OWNER', [alice, { ...bob, role: 'OWNER' }]));
    await fixture.whenStable();

    expect(all('.role.owner')).toHaveLength(2);
    expect(all('.make-owner')).toHaveLength(0);
  });

  it('lets members leave after confirming and goes back to the households', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await open(
      household('MEMBER', [
        { ...alice, me: false },
        { ...bob, me: true },
      ]),
    );

    await click('.leave');
    expect(text()).toContain('Leave Miller family?');
    send('.confirm-leave');
    http.expectOne({ method: 'POST', url: '/api/households/h1/leave' }).flush(null);
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/households']);
  });

  it('shows why leaving was refused', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    await open();

    await click('.leave');
    send('.confirm-leave');
    http
      .expectOne({ method: 'POST', url: '/api/households/h1/leave' })
      .flush({ detail: 'The household needs an owner.' }, { status: 409, statusText: 'Conflict' });
    TestBed.tick();
    // The page loads the household again in case it changed meanwhile.
    http.expectOne('/api/households/h1').flush(household('OWNER', [alice, bob]));
    http.expectOne('/api/households/h1/invitations').flush([]);
    await fixture.whenStable();

    expect(snackBar.open).toHaveBeenCalledWith(
      'The household needs an owner.',
      'OK',
      expect.anything(),
    );
    expect(navigate).not.toHaveBeenCalled();
  });
});
