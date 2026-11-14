import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AddEntryRequest, ShoppingList } from './shopping.model';

/**
 * Commands of the household's shopping list. Each one answers with the whole list as it is afterwards. The list
 * itself is loaded with `httpResource` in the page.
 */
@Injectable({ providedIn: 'root' })
export class ShoppingApi {
  private readonly http = inject(HttpClient);

  /** Adds a product or free text, or adds the quantity to the open entry for the same thing. */
  add(householdId: string, request: AddEntryRequest): Observable<ShoppingList> {
    return this.http.post<ShoppingList>(
      `/api/households/${householdId}/shopping-list/entries`,
      request,
    );
  }

  check(householdId: string, entryId: string): Observable<ShoppingList> {
    return this.http.post<ShoppingList>(`${this.entry(householdId, entryId)}/check`, null);
  }

  uncheck(householdId: string, entryId: string): Observable<ShoppingList> {
    return this.http.post<ShoppingList>(`${this.entry(householdId, entryId)}/uncheck`, null);
  }

  remove(householdId: string, entryId: string): Observable<ShoppingList> {
    return this.http.delete<ShoppingList>(this.entry(householdId, entryId));
  }

  /** The shopping is done: everything in the basket leaves the list. */
  clearChecked(householdId: string): Observable<ShoppingList> {
    return this.http.post<ShoppingList>(
      `/api/households/${householdId}/shopping-list/clear-checked`,
      null,
    );
  }

  private entry(householdId: string, entryId: string): string {
    return `/api/households/${householdId}/shopping-list/entries/${entryId}`;
  }
}
