import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CatalogPage } from './catalog-page';
import { FoodCategory, Product } from './catalog.model';

const VEGETABLES: FoodCategory = {
  id: 'c-veg',
  name: 'Vegetables',
  emoji: '🥦',
  shelfLifeMonths: 12,
};
const MEAT: FoodCategory = { id: 'c-meat', name: 'Meat', emoji: '🥩', shelfLifeMonths: 6 };
const PEAS: Product = { id: 'p1', name: 'Peas', categoryId: 'c-veg' };

describe('CatalogPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CatalogPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(
    categories: FoodCategory[] = [VEGETABLES],
    products: Product[] = [PEAS],
  ): Promise<void> {
    fixture = TestBed.createComponent(CatalogPage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
    http.expectOne('/api/households/h1/food-categories').flush(categories);
    http.expectOne('/api/households/h1/products').flush(products);
    http
      .expectOne('/api/households/h1/catalog')
      .flush({ startedWith: 'en', startedAt: '2026-11-01T10:00:00Z' });
    http.expectOne('/api/catalog/languages').flush([
      { code: 'en', name: 'English' },
      { code: 'de', name: 'Deutsch' },
    ]);
    await fixture.whenStable();
  }

  /** After a command, the page shows the catalog as it is now. */
  async function reloaded(products: Product[] = [PEAS]): Promise<void> {
    TestBed.tick();
    http.expectOne('/api/households/h1/food-categories').flush([VEGETABLES]);
    http.expectOne('/api/households/h1/products').flush(products);
    await fixture.whenStable();
  }

  function element<T extends HTMLElement>(selector: string): T {
    return fixture.nativeElement.querySelector(selector);
  }

  function text(): string {
    return (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  function press(field: HTMLElement, key: string): void {
    field.dispatchEvent(new KeyboardEvent('keydown', { key }));
  }

  /** Opens the food's menu (an overlay outside the component) and chooses Rename. */
  async function startRenaming(name: string): Promise<HTMLInputElement> {
    const food = Array.from(fixture.nativeElement.querySelectorAll('button.food')).find(
      (button) => (button as HTMLElement).textContent?.trim() === name,
    ) as HTMLButtonElement;
    food.click();
    await fixture.whenStable();
    const rename = Array.from(document.querySelectorAll('.mat-mdc-menu-item')).find((item) =>
      item.textContent?.includes('Rename'),
    ) as HTMLButtonElement;
    rename.click();
    await fixture.whenStable();
    return element<HTMLInputElement>('input.rename');
  }

  it('renames a food on Enter, once', async () => {
    await open();
    const field = await startRenaming('Peas');
    field.value = 'Garden peas';

    press(field, 'Enter');
    // Ending the rename removes the field; a browser blurs it on the way.
    field.dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    const request = http.expectOne({ method: 'PUT', url: '/api/households/h1/products/p1' });
    expect(request.request.body).toEqual({ name: 'Garden peas', categoryId: 'c-veg' });
    request.flush({ ...PEAS, name: 'Garden peas' });
    await reloaded([{ ...PEAS, name: 'Garden peas' }]);
    expect(element('input.rename')).toBeNull();
    expect(text()).toContain('Garden peas');
  });

  it('renames a food when leaving the field', async () => {
    await open();
    const field = await startRenaming('Peas');
    field.value = 'Garden peas';

    field.dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    http.expectOne({ method: 'PUT', url: '/api/households/h1/products/p1' }).flush(PEAS);
    await reloaded();
  });

  it('keeps the name on Escape', async () => {
    await open();
    const field = await startRenaming('Peas');
    field.value = 'Garden peas';

    press(field, 'Escape');
    field.dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    http.expectNone({ method: 'PUT', url: '/api/households/h1/products/p1' });
    expect(element('input.rename')).toBeNull();
    expect(text()).toContain('Peas');
  });

  it('adds a food once, however often Enter is pressed', async () => {
    await open();
    const field = element<HTMLInputElement>('.food.add input');
    field.value = 'Corn';

    press(field, 'Enter');
    press(field, 'Enter');
    await fixture.whenStable();

    const request = http.expectOne({ method: 'POST', url: '/api/households/h1/products' });
    expect(request.request.body).toEqual({ name: 'Corn', categoryId: 'c-veg' });
    request.flush({ id: 'p2', name: 'Corn', categoryId: 'c-veg' });
    await reloaded([PEAS, { id: 'p2', name: 'Corn', categoryId: 'c-veg' }]);
    expect(field.value).toBe('');
  });

  it('keeps what was typed while a food was being added', async () => {
    await open();
    const field = element<HTMLInputElement>('.food.add input');
    field.value = 'Corn';
    press(field, 'Enter');
    field.value = 'Carr';

    http
      .expectOne({ method: 'POST', url: '/api/households/h1/products' })
      .flush({ id: 'p2', name: 'Corn', categoryId: 'c-veg' });
    await reloaded();

    expect(field.value).toBe('Carr');
  });

  it('asks before starting over, counting what will be replaced', async () => {
    await open([VEGETABLES], [PEAS]);

    element<HTMLButtonElement>('.start-over-button').click();
    await fixture.whenStable();

    expect(element('[role=alertdialog]').textContent).toContain(
      '1 category and 1 food will be replaced. Start over?',
    );
  });

  it('counts several categories and foods in the plural', async () => {
    await open(
      [VEGETABLES, MEAT],
      [
        PEAS,
        { id: 'p2', name: 'Corn', categoryId: 'c-veg' },
        { id: 'p3', name: 'Beef', categoryId: 'c-meat' },
      ],
    );

    element<HTMLButtonElement>('.start-over-button').click();
    await fixture.whenStable();

    expect(element('[role=alertdialog]').textContent).toContain(
      'All 2 categories and 3 foods will be replaced.',
    );
  });
});
