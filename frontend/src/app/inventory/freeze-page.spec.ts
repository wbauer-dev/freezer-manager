import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { today } from '../shared/dates';
import { FreezePage } from './freeze-page';

describe('FreezePage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<FreezePage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function open(freePersistentLabels = 0): Promise<void> {
    fixture = TestBed.createComponent(FreezePage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
    http
      .expectOne('/api/households/h1/products')
      .flush([{ id: 'p1', name: 'Peas', categoryId: 'c-veg' }]);
    http.expectOne('/api/households/h1/food-categories').flush([
      { id: 'c-veg', name: 'Vegetables', emoji: '🥦', shelfLifeMonths: 12 },
      { id: 'c-mince', name: 'Minced meat', emoji: '🍔', shelfLifeMonths: 3 },
    ]);
    http
      .expectOne('/api/households/h1/freezers')
      .flush([
        { id: 'f1', name: 'Kitchen', compartments: [{ id: 'c1', name: 'Top', itemCount: 0 }] },
      ]);
    http
      .expectOne('/api/households/h1/labels/stock')
      .flush({ freePersistent: freePersistentLabels, persistentInUse: 0 });
    if (freePersistentLabels > 0) {
      // Owned labels come first, so the page asks which ones are free (before waiting: stable means no requests).
      await settle();
      TestBed.tick();
      http.expectOne('/api/households/h1/labels/free').flush(FREE_LABELS);
    }
    await fixture.whenStable();
  }

  const FREE_LABELS = [
    { code: 'K7Q2M9XA', lastContents: 'Beef goulash' },
    { code: 'P3RS1ST3', lastContents: null },
    { code: 'W4TR5BXZ', lastContents: 'Chicken stock' },
  ];

  afterEach(() => http.verify());

  function element<T extends HTMLElement>(selector: string): T {
    return fixture.nativeElement.querySelector(selector);
  }

  async function enter(selector: string, value: string): Promise<void> {
    const field = element<HTMLInputElement | HTMLSelectElement>(selector);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    field.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function click(selector: string): Promise<void> {
    element(selector).click();
    await fixture.whenStable();
  }

  async function submitForm(): Promise<void> {
    element('form').dispatchEvent(new Event('submit'));
    await settle();
  }

  /** The suggestions in the autocomplete panel, which lives in an overlay outside the component. */
  function options(): string[] {
    return Array.from(document.querySelectorAll('mat-option')).map((option) =>
      Array.from(option.querySelectorAll('.free-label-code, .free-label-last'))
        .map((part) => (part.textContent ?? '').trim())
        .join(' '),
    );
  }

  function settle(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve));
  }

  it('freezes an existing product, ignoring case', async () => {
    await open();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await enter('#product', 'peas');
    await enter('#portions', '3');

    expect(element('[data-category]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('recommended 12 months for “Vegetables”');

    await submitForm();
    const request = http.expectOne({ method: 'POST', url: '/api/households/h1/items' });
    expect(request.request.body).toEqual({
      productId: 'p1',
      freezerId: 'f1',
      compartmentId: 'c1',
      portions: 3,
      gramsPerPortion: null,
      frozenOn: today(),
      bestBefore: null,
      note: null,
      labelCode: null,
      photoId: null,
    });
    request.flush({ id: 'i1' });
    await settle();

    expect(navigate).toHaveBeenCalledWith(['..', 'items', 'i1'], expect.anything());
  });

  it('adds a new product to the catalog before freezing it', async () => {
    await open();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await enter('#product', 'Minced meat');
    await click('[data-category="Minced meat"]');
    await enter('#note', 'for Bolognese');

    await submitForm();
    const define = http.expectOne({ method: 'POST', url: '/api/households/h1/products' });
    expect(define.request.body).toEqual({ name: 'Minced meat', categoryId: 'c-mince' });
    define.flush({ id: 'p2', name: 'Minced meat', categoryId: 'c-mince' });
    await settle();

    const freeze = http.expectOne({ method: 'POST', url: '/api/households/h1/items' });
    expect(freeze.request.body).toMatchObject({ productId: 'p2', note: 'for Bolognese' });
    freeze.flush({ id: 'i2' });
    await settle();

    // The product list is reloaded so the new product shows up next time.
    http
      .match({ method: 'GET', url: '/api/households/h1/products' })
      .forEach((reload) => reload.flush([]));
  });

  it('asks for a category when the product is new', async () => {
    await open();
    await enter('#product', 'Fish fingers');

    await submitForm();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Choose a category for the new product.');
  });

  it('suggests free labels by their code or by what they held last', async () => {
    await open(3);
    const field = fixture.nativeElement.querySelector('#label-code') as HTMLInputElement;
    field.focus();
    await fixture.whenStable();
    expect(options()).toEqual([
      'K7Q2-M9XA last: Beef goulash',
      'P3RS-1ST3 not used yet',
      'W4TR-5BXZ last: Chicken stock',
    ]);

    await enter('#label-code', 'goul');
    expect(options()).toEqual(['K7Q2-M9XA last: Beef goulash']);
    await enter('#label-code', 'w4t');
    expect(options()).toEqual(['W4TR-5BXZ last: Chicken stock']);

    (document.querySelector('mat-option') as HTMLElement).click();
    await fixture.whenStable();
    expect(field.value).toBe('W4TR-5BXZ');
  });

  it('prefers a persistent label the household owns and can print a contents sticker for it', async () => {
    await open(3);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    expect(fixture.nativeElement.textContent).toContain('3 free persistent labels');

    await enter('#product', 'peas');
    await enter('#label-code', 'p3rs-1st3');
    fixture.nativeElement.querySelector('.print-contents input').click();
    await fixture.whenStable();
    await submitForm();

    http.expectOne('/api/labels/P3RS1ST3').flush({
      code: 'P3RS1ST3',
      householdId: 'h1',
      kind: 'PERSISTENT',
      state: 'FREE',
      itemId: null,
    });
    await settle();
    const freeze = http.expectOne({ method: 'POST', url: '/api/households/h1/items' });
    expect(freeze.request.body).toMatchObject({ productId: 'p1', labelCode: 'P3RS1ST3' });
    freeze.flush({ id: 'i3' });
    await settle();

    const sticker = http.expectOne({ method: 'POST', url: '/api/households/h1/label-sheet/queue' });
    expect(sticker.request.body).toEqual({
      code: 'P3RS1ST3',
      layout: 'CONTENT_ONLY',
      itemId: 'i3',
    });
    sticker.flush(null);
    await settle();
  });
});
