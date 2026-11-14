import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { addMonths, today } from '../shared/dates';
import { EatFirstPage } from './eat-first-page';
import { EatFirstEntry } from './inventory.model';

const PEAS: EatFirstEntry = {
  itemId: 'i1',
  productId: 'p1',
  photoId: null,
  productName: 'Peas',
  note: null,
  remainingPortions: 2,
  gramsPerPortion: null,
  frozenOn: today(),
  bestBefore: addMonths(today(), 12),
  expiryStatus: 'FRESH',
  freezerId: 'f1',
  freezerName: 'Kitchen',
  compartmentId: 'c1',
  compartmentName: 'Top',
};

const KITCHEN = {
  id: 'f1',
  name: 'Kitchen',
  compartments: [{ id: 'c1', name: 'Top', itemCount: 1 }],
};

describe('EatFirstPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EatFirstPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EatFirstPage);
    fixture.componentRef.setInput('householdId', 'h1');
    TestBed.tick();
  });

  afterEach(() => http.verify());

  function text(): string {
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  function catalog(): void {
    http.expectOne('/api/households/h1/products').flush([]);
    http.expectOne('/api/households/h1/food-categories').flush([]);
  }

  it('says when the freezers could not be loaded, and tries again', async () => {
    http.expectOne('/api/households/h1/eat-first').flush([PEAS]);
    http
      .expectOne('/api/households/h1/freezers')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    catalog();
    await fixture.whenStable();

    expect(text()).toContain('The freezer contents could not be loaded.');
    fixture.nativeElement.querySelector('app-load-error button').click();
    TestBed.tick();
    http.expectOne('/api/households/h1/freezers').flush([KITCHEN]);
    await fixture.whenStable();

    expect(text()).not.toContain('could not be loaded');
    expect(text()).toContain('Peas');
  });
});
