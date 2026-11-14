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

  it('shows the food on a scanned label and takes portions out', async () => {
    await enterCode('k7q2-m9xa');
    http.expectOne('/api/labels/K7Q2M9XA').flush({
      code: 'K7Q2M9XA',
      householdId: 'h1',
      kind: 'ONE_TIME',
      state: 'ATTACHED',
      itemId: 'i1',
    });
    http.expectOne('/api/households/h1/items/i1').flush(PEAS);
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

    expect(text()).toContain('Peas');
    expect(text()).toContain("Alice's flat");
    expect(text()).toContain('3 of 4 left');

    fixture.nativeElement.querySelector('.take-out-button').click();
    const takeOut = http.expectOne({ method: 'POST', url: '/api/households/h1/items/i1/take-out' });
    expect(takeOut.request.body).toEqual({ portions: 1 });
    takeOut.flush({ ...PEAS, remainingPortions: 2 });
    await fixture.whenStable();

    expect(text()).toContain('2 of 4 left');
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
