import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { LabelSheet } from './labeling.model';
import { LabelsPage } from './labels-page';

const STICKER = {
  code: 'K7Q2M9XA',
  layout: 'QR_AND_CONTENT',
  title: 'Peas',
  frozenOn: '2026-11-01',
  bestBefore: '2027-11-01',
};

function sheet(usedPositions: number[], nextPosition: number): LabelSheet {
  return { columns: 3, rows: 8, usedPositions, nextPosition, queue: [STICKER] } as LabelSheet;
}

describe('LabelsPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<LabelsPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(current: LabelSheet | null): Promise<void> {
    fixture = TestBed.createComponent(LabelsPage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
    const request = http.expectOne('/api/households/h1/label-sheet');
    if (current) {
      request.flush(current);
    } else {
      request.flush(null, { status: 503, statusText: 'Service Unavailable' });
    }
    http
      .expectOne('/api/households/h1/labels/stock')
      .flush({ freePersistent: 0, persistentInUse: 0 });
    await fixture.whenStable();
  }

  function text(): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  it('tells where printing starts on the current sheet', async () => {
    await open(sheet([0, 1], 2));

    expect(text()).toContain('Starts at position 3 of your current sheet.');
  });

  it('tells that printing starts on a new sheet when the current one is full', async () => {
    const all = Array.from({ length: 24 }, (_, position) => position);
    await open(sheet(all, -1));

    expect(text()).toContain('Starts on a new sheet.');
    expect(text()).not.toContain('position 0');
  });

  it('shows why printing failed', async () => {
    await open(sheet([], 0));

    fixture.nativeElement.querySelector('button.print').click();
    const problem = {
      title: 'Conflict',
      status: 409,
      detail: 'Someone else printed these labels a moment ago.',
    };
    http
      .expectOne({ method: 'POST', url: '/api/households/h1/label-sheet/print' })
      .flush(new Blob([JSON.stringify(problem)], { type: 'application/problem+json' }), {
        status: 409,
        statusText: 'Conflict',
      });

    await vi.waitFor(() =>
      expect(document.querySelector('.mat-mdc-snack-bar-label')?.textContent).toContain(
        'Someone else printed these labels a moment ago.',
      ),
    );
    TestBed.tick();
    http.expectOne('/api/households/h1/label-sheet').flush(sheet([], 0));
  });

  it('says when the sheet could not be loaded, and tries again', async () => {
    await open(null);
    expect(text()).toContain('The print queue and sheet could not be loaded.');

    fixture.nativeElement.querySelector('app-load-error button').click();
    TestBed.tick();
    http.expectOne('/api/households/h1/label-sheet').flush(sheet([], 0));
    await fixture.whenStable();

    expect(text()).not.toContain('could not be loaded');
    expect(text()).toContain('Print queue');
  });
});
