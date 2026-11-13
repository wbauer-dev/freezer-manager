import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FoodCategory, Product, ResetMode } from './catalog.model';

/** Changes to a household's catalog. Every member may make them. */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  defineCategory(
    householdId: string,
    category: Omit<FoodCategory, 'id'>,
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

  removeCategory(householdId: string, categoryId: string): Observable<void> {
    return this.http.delete<void>(`/api/households/${householdId}/food-categories/${categoryId}`);
  }

  defineProduct(householdId: string, name: string, categoryId: string): Observable<Product> {
    return this.http.post<Product>(`/api/households/${householdId}/products`, { name, categoryId });
  }

  editProduct(householdId: string, product: Product): Observable<Product> {
    const { id, ...changes } = product;
    return this.http.put<Product>(`/api/households/${householdId}/products/${id}`, changes);
  }

  removeProduct(householdId: string, productId: string): Observable<void> {
    return this.http.delete<void>(`/api/households/${householdId}/products/${productId}`);
  }

  reset(householdId: string, mode: ResetMode, language: string | null): Observable<void> {
    return this.http.post<void>(`/api/households/${householdId}/catalog/reset`, { mode, language });
  }
}
