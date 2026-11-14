import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ShoppingPage } from './shopping-page';
import { ShoppingEntry, ShoppingList } from './shopping.model';

describe('ShoppingPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ShoppingPage>;

  const peas: ShoppingEntry = {
    id: 'e1',
    productId: 'p1',
    name: 'Peas',
    quantity: '500 g',
    source: 'MANUAL',
    addedBy: 'Alice',
    addedAt: '2026-11-14T09:00:00Z',
    checked: false,
    checkedBy: null,
    checkedAt: null,
  };
  const milk: ShoppingEntry = {
    ...peas,
    id: 'e2',
    productId: null,
    name: 'Milk',
    quantity: null,
    checked: true,
    checkedBy: 'Bob',
    checkedAt: '2026-11-14T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(list: ShoppingList): Promise<void> {
    fixture = TestBed.createComponent(ShoppingPage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
    http.expectOne('/api/households/h1/shopping-list').flush(list);
    http
      .expectOne('/api/households/h1/products')
      .flush([{ id: 'p1', name: 'Peas', categoryId: 'c-veg' }]);
    http
      .expectOne('/api/households/h1/food-categories')
      .flush([{ id: 'c-veg', name: 'Vegetables', emoji: '🥦', shelfLifeMonths: 12 }]);
    await fixture.whenStable();
  }

  function element<T extends HTMLElement>(selector: string): T {
    return fixture.nativeElement.querySelector(selector);
  }

  function text(): string {
    return (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  async function enter(selector: string, value: string): Promise<void> {
    const field = element<HTMLInputElement>(selector);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('shows what to buy and what is in the basket', async () => {
    await open({ entries: [peas, milk] });

    expect(text()).toContain('To buy');
    expect(text()).toContain('Peas');
    expect(text()).toContain('500 g');
    expect(text()).toContain('added by Alice');
    expect(element('.basket').textContent).toContain('Milk');
    expect(element('.basket').textContent).toContain('ticked off by Bob');
    expect(element('.entry .thing').textContent).toContain('🥦');
  });

  it('invites to add something when the list is empty', async () => {
    await open({ entries: [] });

    expect(text()).toContain('Nothing to buy');
  });

  it('adds a known product by its id, ignoring case', async () => {
    await open({ entries: [] });
    await enter('#shopping-food', 'peas');
    await enter('#shopping-quantity', ' 1 kg ');

    await submit();

    const request = http.expectOne({
      method: 'POST',
      url: '/api/households/h1/shopping-list/entries',
    });
    expect(request.request.body).toEqual({ productId: 'p1', quantity: '1 kg' });
    request.flush({ entries: [peas] });
    await fixture.whenStable();
    expect(element<HTMLInputElement>('#shopping-food').value).toBe('');
    expect(text()).toContain('Peas');
  });

  it('adds anything else as free text', async () => {
    await open({ entries: [] });
    await enter('#shopping-food', 'Milk');

    await submit();

    const request = http.expectOne({
      method: 'POST',
      url: '/api/households/h1/shopping-list/entries',
    });
    expect(request.request.body).toEqual({ name: 'Milk', quantity: undefined });
    request.flush({ entries: [{ ...milk, checked: false, checkedBy: null, checkedAt: null }] });
  });

  it('ticks an entry off right away and takes the answer of the server', async () => {
    await open({ entries: [peas] });

    element('.entry .tick').click();
    await fixture.whenStable();

    expect(element('.basket').textContent).toContain('Peas');
    const request = http.expectOne({
      method: 'POST',
      url: '/api/households/h1/shopping-list/entries/e1/check',
    });
    request.flush({ entries: [{ ...peas, checked: true, checkedBy: 'Alice' }] });
    await fixture.whenStable();
    expect(element('.basket').textContent).toContain('ticked off by Alice');
  });

  it('clears the basket when the shopping is done', async () => {
    await open({ entries: [peas, milk] });

    element<HTMLButtonElement>('.basket .done').click();

    http
      .expectOne({ method: 'POST', url: '/api/households/h1/shopping-list/clear-checked' })
      .flush({ entries: [peas] });
    await fixture.whenStable();
    expect(element('.basket')).toBeNull();
  });
});
