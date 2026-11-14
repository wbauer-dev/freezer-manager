import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FoodCategory, Product, ResetMode } from './catalog.model';

/**
 * Changes to a household's catalog. Every member may make them.
 *
 * Changing or removing a category or product sends the version the member last saw. If another member changed it in
 * the meantime, the backend answers 409 with the code `catalog.category.changed` or `catalog.product.changed`.
 * A change answers with the item in its new version.
 */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  defineCategory(
    householdId: string,
    category: Omit<FoodCategory, 'id' | 'version'>,
  ): Observable<FoodCategory> {
    return this.http.post<FoodCategory>(`/api/households/${householdId}/food-categories`, category);
  }

  editCategory(householdId: string, category: FoodCategory): Observable<FoodCategory> {
    const { id, ...changes } = category;
    return this.http.put<FoodCategory>(
      `/api/households/${householdId}/food-categories/${id}`,
      changes,
    );
  }

  removeCategory(
    householdId: string,
    category: Pick<FoodCategory, 'id' | 'version'>,
  ): Observable<void> {
    return this.http.delete<void>(`/api/households/${householdId}/food-categories/${category.id}`, {
      params: { version: category.version },
    });
  }

  defineProduct(householdId: string, name: string, categoryId: string): Observable<Product> {
    return this.http.post<Product>(`/api/households/${householdId}/products`, { name, categoryId });
  }

  editProduct(householdId: string, product: Product): Observable<Product> {
    const { id, ...changes } = product;
    return this.http.put<Product>(`/api/households/${householdId}/products/${id}`, changes);
  }

  removeProduct(householdId: string, product: Pick<Product, 'id' | 'version'>): Observable<void> {
    return this.http.delete<void>(`/api/households/${householdId}/products/${product.id}`, {
      params: { version: product.version },
    });
  }

  reset(householdId: string, mode: ResetMode, language: string | null): Observable<void> {
    return this.http.post<void>(`/api/households/${householdId}/catalog/reset`, { mode, language });
  }
}
