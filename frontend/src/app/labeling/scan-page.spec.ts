import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { LabelState } from './labeling.model';
import { ScanPage } from './scan-page';

/** A phone camera opened /s/K7Q2M9XA: the page sends the member where the label leads. */
describe('ScanPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ScanPage>;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ScanPage);
    fixture.componentRef.setInput('code', 'K7Q2M9XA');
    TestBed.tick();
  });

  afterEach(() => http.verify());

  function label(state: LabelState, itemId: string | null): void {
    http.expectOne('/api/labels/K7Q2M9XA').flush({
      code: 'K7Q2M9XA',
      householdId: 'h1',
      kind: 'ONE_TIME',
      state,
      itemId,
    });
  }

  function text(): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  it('shows the item a label is on', async () => {
    label('ATTACHED', 'i1');
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/households', 'h1', 'items', 'i1'], {
      replaceUrl: true,
    });
  });

  it('offers to freeze something with a free label', async () => {
    label('FREE', null);
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/households', 'h1', 'freeze'], {
      queryParams: { label: 'K7Q2M9XA' },
      replaceUrl: true,
    });
  });

  it('explains a used label and links to the item’s history', async () => {
    label('USED_UP', 'i1');
    await fixture.whenStable();

    expect(navigate).not.toHaveBeenCalled();
    expect(text()).toContain('This label has been used');
    const history: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    expect(history.getAttribute('href')).toBe('/households/h1/items/i1');
  });

  it('does not tell whether an unknown label exists elsewhere', async () => {
    http.expectOne('/api/labels/K7Q2M9XA').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(navigate).not.toHaveBeenCalled();
    expect(text()).toContain('Unknown label');
    expect(text()).toContain('K7Q2-M9XA doesn’t belong to any of your households.');
  });

  it('says when the label could not be read', async () => {
    http
      .expectOne('/api/labels/K7Q2M9XA')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(text()).toContain('The label could not be read');
  });
});
