import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DiscardReason, FreezeRequest, FrozenItem } from './inventory.model';

/**
 * Commands against the inventory. They mirror the domain's language: freeze, take out, undo, discard.
 * Queries are made with httpResource in the pages.
 */
@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);

  setUpFreezer(
    householdId: string,
    name: string,
    compartments: string[],
  ): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`/api/households/${householdId}/freezers`, {
      name,
      compartments,
    });
  }

  removeFreezer(householdId: string, freezerId: string): Observable<void> {
    return this.http.delete<void>(`/api/households/${householdId}/freezers/${freezerId}`);
  }

  addCompartment(householdId: string, freezerId: string, name: string): Observable<void> {
    return this.http.post<void>(
      `/api/households/${householdId}/freezers/${freezerId}/compartments`,
      { name },
    );
  }

  removeCompartment(
    householdId: string,
    freezerId: string,
    compartmentId: string,
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/households/${householdId}/freezers/${freezerId}/compartments/${compartmentId}`,
    );
  }

  rearrangeCompartments(
    householdId: string,
    freezerId: string,
    compartmentIds: string[],
  ): Observable<void> {
    return this.http.post<void>(
      `/api/households/${householdId}/freezers/${freezerId}/rearrange-compartments`,
      { compartmentIds },
    );
  }

  item(householdId: string, itemId: string): Observable<FrozenItem> {
    return this.http.get<FrozenItem>(`/api/households/${householdId}/items/${itemId}`);
  }

  freeze(householdId: string, request: FreezeRequest): Observable<FrozenItem> {
    return this.http.post<FrozenItem>(`/api/households/${householdId}/items`, request);
  }

  takeOut(householdId: string, itemId: string, portions: number): Observable<FrozenItem> {
    return this.http.post<FrozenItem>(`/api/households/${householdId}/items/${itemId}/take-out`, {
      portions,
    });
  }

  undoTakeOut(householdId: string, itemId: string): Observable<FrozenItem> {
    return this.http.post<FrozenItem>(
      `/api/households/${householdId}/items/${itemId}/undo-take-out`,
      {},
    );
  }

  attachPhoto(householdId: string, itemId: string, photoId: string): Observable<FrozenItem> {
    return this.http.post<FrozenItem>(`/api/households/${householdId}/items/${itemId}/photo`, {
      photoId,
    });
  }

  discard(householdId: string, itemId: string, reason: DiscardReason): Observable<FrozenItem> {
    return this.http.post<FrozenItem>(`/api/households/${householdId}/items/${itemId}/discard`, {
      reason,
    });
  }
}
