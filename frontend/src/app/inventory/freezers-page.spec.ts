import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { addMonths, today } from '../shared/dates';
import { FreezersPage } from './freezers-page';
import { EatFirstEntry } from './inventory.model';

describe('FreezersPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<FreezersPage>;

  function item(id: string, name: string, compartmentId: string, months: number): EatFirstEntry {
    return {
      itemId: id,
      productId: name === 'Peas' ? 'p-peas' : 'p-other',
      photoId: null,
      productName: name,
      note: null,
      remainingPortions: 2,
      gramsPerPortion: null,
      frozenOn: today(),
      bestBefore: addMonths(today(), months),
      expiryStatus: 'FRESH',
      freezerId: 'f1',
      freezerName: 'Kitchen',
      compartmentId,
      compartmentName: compartmentId === 'top' ? 'Top' : 'Bottom',
    };
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FreezersPage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
    http.expectOne('/api/households/h1/freezers').flush([
      {
        id: 'f1',
        name: 'Kitchen',
        compartments: [
          { id: 'top', name: 'Top', itemCount: 2 },
          { id: 'bottom', name: 'Bottom', itemCount: 0 },
        ],
      },
    ]);
    http
      .expectOne('/api/households/h1/eat-first')
      .flush([item('i1', 'Goulash', 'top', 3), item('i2', 'Peas', 'top', 12)]);
    http
      .expectOne('/api/households/h1/products')
      .flush([{ id: 'p-peas', name: 'Peas', categoryId: 'c-veg' }]);
    http
      .expectOne('/api/households/h1/food-categories')
      .flush([{ id: 'c-veg', name: 'Vegetables', emoji: '🥦', shelfLifeMonths: 12 }]);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function toggles(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.drawer-toggle'));
  }

  it('shows what is in a drawer when it is opened', async () => {
    const [top, bottom] = toggles();
    expect(fixture.nativeElement.querySelector('.drawer-items')).toBeNull();
    expect(bottom.disabled).toBe(true);

    top.click();
    await fixture.whenStable();

    expect(top.getAttribute('aria-expanded')).toBe('true');
    const names = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.item-name')).map(
      (name) => name.textContent?.trim(),
    );
    expect(names).toEqual(['Goulash', 'Peas']);
    const links = fixture.nativeElement.querySelectorAll('.drawer-item');
    expect(links[1].getAttribute('href')).toBe('/items/i2');
    expect(links[1].textContent).toContain('🥦');

    top.click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.drawer-items')).toBeNull();
  });
});
