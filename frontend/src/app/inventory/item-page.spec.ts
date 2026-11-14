import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { FrozenItem } from './inventory.model';
import { ItemPage } from './item-page';

const PEAS: FrozenItem = {
  id: 'i1',
  productId: 'p1',
  photoId: null,
  productName: 'Peas',
  note: null,
  labelCode: null,
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
};

describe('ItemPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ItemPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(item: FrozenItem): Promise<void> {
    fixture = TestBed.createComponent(ItemPage);
    fixture.componentRef.setInput('householdId', 'h1');
    fixture.componentRef.setInput('itemId', item.id);
    TestBed.tick();
    http.expectOne(`/api/households/h1/items/${item.id}`).flush(item);
    http
      .expectOne('/api/households/h1/products')
      .flush([{ id: 'p1', name: 'Peas', categoryId: 'c1' }]);
    http
      .expectOne('/api/households/h1/food-categories')
      .flush([{ id: 'c1', name: 'Vegetables', emoji: '🥦', shelfLifeMonths: 12 }]);
    await fixture.whenStable();
  }

  function button(text: string): HTMLButtonElement {
    const buttons = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'));
    return buttons.find((button) => button.textContent?.includes(text))!;
  }

  /** The Undo button of the snackbar, which lives in an overlay outside the component. */
  function snackBarUndo(): HTMLButtonElement | null {
    return document.querySelector('.mat-mdc-snack-bar-action');
  }

  it('withdraws the snackbar’s offer to undo once the take-out is undone on the page', async () => {
    await open(PEAS);
    button('Take out 1 portion').click();
    http
      .expectOne({ method: 'POST', url: '/api/households/h1/items/i1/take-out' })
      .flush({ ...PEAS, remainingPortions: 2, canUndoTakeOut: true });
    await fixture.whenStable();
    expect(snackBarUndo()).not.toBeNull();

    button('Undo take-out').click();
    const undo = http.expectOne({
      method: 'POST',
      url: '/api/households/h1/items/i1/undo-take-out',
    });

    // Gone before the answer arrives, so it can't undo the withdrawal before.
    await vi.waitFor(() => expect(snackBarUndo()).toBeNull());
    undo.flush(PEAS);
    await fixture.whenStable();
  });
});
