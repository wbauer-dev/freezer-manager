import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';

import { JoinPage } from './join-page';

describe('JoinPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<JoinPage>;
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

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
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(JoinPage);
    fixture.componentRef.setInput('code', 'q3J7d0lYzPq8n1Kx4bVt_w');
    TestBed.tick();
  });

  afterEach(() => http.verify());

  function accept() {
    return http.expectOne({
      method: 'POST',
      url: '/api/invitations/q3J7d0lYzPq8n1Kx4bVt_w/accept',
    });
  }

  function text(): string {
    return (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  it('accepts the invitation and opens the household', async () => {
    accept().flush({ id: 'h1', name: 'Miller family', myRole: 'MEMBER', memberCount: 2 });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/households', 'h1'], { replaceUrl: true });
    expect(snackBar.open).toHaveBeenCalledWith(
      'You’re a member of Miller family',
      undefined,
      expect.anything(),
    );
  });

  it('explains an invitation that does not work', async () => {
    accept().flush(
      { detail: 'This invitation does not work (any more).' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();

    expect(text()).toContain('This invitation doesn’t work');
    expect(text()).toContain('This invitation does not work (any more).');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('offers to try again after other errors', async () => {
    accept().flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(text()).toContain('You could not join the household');

    fixture.nativeElement.querySelector('button').click();
    accept().flush({ id: 'h1', name: 'Miller family', myRole: 'MEMBER', memberCount: 2 });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/households', 'h1'], { replaceUrl: true });
  });
});
