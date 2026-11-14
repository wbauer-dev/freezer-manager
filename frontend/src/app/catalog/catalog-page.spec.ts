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
  version: 2,
};
const MEAT: FoodCategory = {
  id: 'c-meat',
  name: 'Meat',
  emoji: '🥩',
  shelfLifeMonths: 6,
  version: 0,
};
const PEAS: Product = { id: 'p1', name: 'Peas', categoryId: 'c-veg', version: 3 };
const CORN: Product = { id: 'p2', name: 'Corn', categoryId: 'c-veg', version: 0 };

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

  /**
   * Opens the food's menu (an overlay outside the component) and chooses an item. While the catalog is being loaded,
   * the page is not stable: pass a settle function that does not wait for it.
   */
  async function choose(
    name: string,
    menuItem: string,
    settle: () => Promise<unknown> = () => fixture.whenStable(),
  ): Promise<void> {
    const food = Array.from(fixture.nativeElement.querySelectorAll('button.food')).find(
      (button) => (button as HTMLElement).textContent?.trim() === name,
    ) as HTMLButtonElement;
    food.click();
    await settle();
    const item = Array.from(document.querySelectorAll('.mat-mdc-menu-item')).find((each) =>
      each.textContent?.includes(menuItem),
    ) as HTMLButtonElement;
    item.click();
    await settle();
  }

  async function startRenaming(
    name: string,
    settle?: () => Promise<unknown>,
  ): Promise<HTMLInputElement> {
    await choose(name, 'Rename', settle);
    return element<HTMLInputElement>('input.rename');
  }

  /** The text of the snackbar, which lives in an overlay outside the component. */
  function snackBarText(): string | null | undefined {
    return document.querySelector('.mat-mdc-snack-bar-label')?.textContent;
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
    expect(request.request.body).toEqual({ name: 'Garden peas', categoryId: 'c-veg', version: 3 });
    request.flush({ ...PEAS, name: 'Garden peas', version: 4 });
    await reloaded([{ ...PEAS, name: 'Garden peas', version: 4 }]);
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

  it('changes a food in the version the answer brings, before the catalog is loaded again', async () => {
    await open();
    let field = await startRenaming('Peas');
    field.value = 'Garden peas';
    press(field, 'Enter');
    await fixture.whenStable();
    http
      .expectOne({ method: 'PUT', url: '/api/households/h1/products/p1' })
      .flush({ ...PEAS, name: 'Garden peas', version: 4 });
    // The catalog is being loaded again, but has not arrived yet.
    const tick = async () => TestBed.tick();
    await tick();

    field = await startRenaming('Garden peas', tick);
    field.value = 'Sweet peas';
    press(field, 'Enter');
    await tick();

    const request = http.expectOne({ method: 'PUT', url: '/api/households/h1/products/p1' });
    expect(request.request.body.version).toBe(4);
    request.flush({ ...PEAS, name: 'Sweet peas', version: 5 });
    TestBed.tick();
    http.expectOne('/api/households/h1/food-categories').flush([VEGETABLES]);
    // The second answer took over from the first loading of the foods, which was cancelled.
    const loads = http.match('/api/households/h1/products').filter((load) => !load.cancelled);
    expect(loads.length).toBe(1);
    loads[0].flush([{ ...PEAS, name: 'Sweet peas', version: 5 }]);
    await fixture.whenStable();
    expect(text()).toContain('Sweet peas');
  });

  it('removes a food in the version it shows', async () => {
    await open();

    await choose('Peas', 'Remove');

    const request = http.expectOne(
      (each) => each.method === 'DELETE' && each.url === '/api/households/h1/products/p1',
    );
    expect(request.request.params.get('version')).toBe('3');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await reloaded([]);
  });

  it('shows what another member changed in the meantime instead of overwriting it', async () => {
    await open();
    const field = await startRenaming('Peas');
    field.value = 'Garden peas';
    press(field, 'Enter');
    await fixture.whenStable();

    http.expectOne({ method: 'PUT', url: '/api/households/h1/products/p1' }).flush(
      {
        detail: 'Someone else changed this food in the meantime. Here is the current version.',
        code: 'catalog.product.changed',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await reloaded([{ ...PEAS, name: 'Frozen peas', version: 4 }]);

    expect(text()).toContain('Frozen peas');
    expect(snackBarText()).toContain(
      'Someone else changed this food in the meantime. Here is the current version.',
    );
  });

  it('closes the editor and shows the current category if another member changed it', async () => {
    await open();
    element<HTMLButtonElement>('button[aria-label="Edit Vegetables"]').click();
    await fixture.whenStable();

    element<HTMLFormElement>('form.editor').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = http.expectOne({
      method: 'PUT',
      url: '/api/households/h1/food-categories/c-veg',
    });
    expect(request.request.body).toEqual({
      name: 'Vegetables',
      emoji: '🥦',
      shelfLifeMonths: 12,
      version: 2,
    });
    request.flush(
      {
        detail: 'Someone else changed this category in the meantime. Here is the current version.',
        code: 'catalog.category.changed',
      },
      { status: 409, statusText: 'Conflict' },
    );
    TestBed.tick();
    http
      .expectOne('/api/households/h1/food-categories')
      .flush([{ ...VEGETABLES, name: 'Greens', version: 3 }]);
    await fixture.whenStable();

    expect(element('form.editor')).toBeNull();
    expect(text()).toContain('Greens');
    expect(snackBarText()).toContain(
      'Someone else changed this category in the meantime. Here is the current version.',
    );
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
    request.flush(CORN);
    await reloaded([PEAS, CORN]);
    expect(field.value).toBe('');
  });

  it('keeps what was typed while a food was being added', async () => {
    await open();
    const field = element<HTMLInputElement>('.food.add input');
    field.value = 'Corn';
    press(field, 'Enter');
    field.value = 'Carr';

    http.expectOne({ method: 'POST', url: '/api/households/h1/products' }).flush(CORN);
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
      [PEAS, CORN, { id: 'p3', name: 'Beef', categoryId: 'c-meat', version: 0 }],
    );

    element<HTMLButtonElement>('.start-over-button').click();
    await fixture.whenStable();

    expect(element('[role=alertdialog]').textContent).toContain(
      'All 2 categories and 3 foods will be replaced.',
    );
  });
});
