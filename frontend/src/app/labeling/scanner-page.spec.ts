import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { FrozenItem } from '../inventory/inventory.model';
import { ScannerPage } from './scanner-page';

const PEAS = {
  id: 'i1',
  productId: 'p1',
  photoId: null,
  productName: 'Peas',
  note: null,
  labelCode: 'K7Q2M9XA',
  state: 'IN_FREEZER',
  initialPortions: 4,
  remainingPortions: 3,
  gramsPerPortion: null,
  frozenOn: '2026-10-01',
  bestBefore: '2027-10-01',
  bestBeforeOverridden: false,
  expiryStatus: 'FRESH',
  freezerId: 'f1',
  freezerName: 'Kitchen',
  compartmentId: 'c1',
  compartmentName: 'Top drawer',
  discardReason: null,
  removedAt: null,
  withdrawals: [],
  canUndoTakeOut: false,
} as unknown as FrozenItem;

const GOULASH = {
  ...PEAS,
  id: 'i2',
  productName: 'Beef goulash',
  labelCode: 'W4TR5BXZ',
  initialPortions: 6,
  remainingPortions: 5,
} as FrozenItem;

/** The test browser has no camera, so codes are entered by hand — the same way as when a sticker is damaged. */
describe('ScannerPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ScannerPage>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ScannerPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function enterCode(code: string): Promise<void> {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('.manual input');
    input.value = code;
    fixture.nativeElement.querySelector('.manual').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function text(): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  /** Scans the label of an item in the freezer and answers the requests that show it. */
  async function scan(item: FrozenItem, typed = item.labelCode!): Promise<void> {
    await enterCode(typed);
    http.expectOne(`/api/labels/${item.labelCode}`).flush({
      code: item.labelCode,
      householdId: 'h1',
      kind: 'ONE_TIME',
      state: 'ATTACHED',
      itemId: item.id,
    });
    http.expectOne(`/api/households/h1/items/${item.id}`).flush(item);
    http
      .expectOne('/api/households/h1/products')
      .flush([{ id: 'p1', name: 'Peas', categoryId: 'c1' }]);
    http
      .expectOne('/api/households/h1/food-categories')
      .flush([{ id: 'c1', name: 'Vegetables', emoji: '🥦', shelfLifeMonths: 12 }]);
    http
      .expectOne('/api/households/h1')
      .flush({ id: 'h1', name: "Alice's flat", foundedAt: '', members: [] });
    await fixture.whenStable();
  }

  async function takeOut(item: FrozenItem): Promise<void> {
    fixture.nativeElement.querySelector('.take-out-button').click();
    const request = http.expectOne({
      method: 'POST',
      url: `/api/households/h1/items/${item.id}/take-out`,
    });
    expect(request.request.body).toEqual({ portions: 1 });
    request.flush({ ...item, remainingPortions: item.remainingPortions - 1, canUndoTakeOut: true });
    await fixture.whenStable();
  }

  async function scanNext(): Promise<void> {
    const buttons = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((button) => button.textContent?.includes('Scan next'))!.click();
    await fixture.whenStable();
  }

  /** The Undo button of the snackbar, which lives in an overlay outside the component. */
  function undoButton(): HTMLButtonElement | null {
    return document.querySelector('.mat-mdc-snack-bar-action');
  }

  it('shows the food on a scanned label and takes portions out', async () => {
    await scan(PEAS, 'k7q2-m9xa');

    expect(text()).toContain('Peas');
    expect(text()).toContain("Alice's flat");
    expect(text()).toContain('3 of 4 left');

    await takeOut(PEAS);

    expect(text()).toContain('2 of 4 left');
  });

  it('keeps showing the next label when an earlier take-out is undone', async () => {
    await scan(PEAS);
    await takeOut(PEAS);
    undoButton()!.click();
    const undo = http.expectOne({
      method: 'POST',
      url: '/api/households/h1/items/i1/undo-take-out',
    });

    // The next label is scanned before the undo has gone through.
    await scanNext();
    await scan(GOULASH);
    undo.flush(PEAS);
    await fixture.whenStable();

    expect(text()).toContain('Beef goulash');
    expect(text()).not.toContain('Peas');
    expect(text()).toContain('5 of 6 left');
  });

  it('withdraws the offer to undo when the next label is scanned', async () => {
    await scan(PEAS);
    await takeOut(PEAS);
    expect(undoButton()).not.toBeNull();

    await scanNext();

    await vi.waitFor(() => expect(undoButton()).toBeNull());
  });

  it('offers to freeze something with a free label', async () => {
    await enterCode('P3RS1ST3');
    http.expectOne('/api/labels/P3RS1ST3').flush({
      code: 'P3RS1ST3',
      householdId: 'h1',
      kind: 'PERSISTENT',
      state: 'FREE',
      itemId: null,
    });
    await fixture.whenStable();

    expect(text()).toContain('This label is free');
    const freeze: HTMLAnchorElement = fixture.nativeElement.querySelector('a.cta');
    expect(freeze.getAttribute('href')).toBe('/households/h1/freeze?label=P3RS1ST3');
  });

  it('explains QR codes that are not labels', async () => {
    await enterCode('https://example.com/menu');

    expect(text()).toContain('Not one of our labels');
  });
});
