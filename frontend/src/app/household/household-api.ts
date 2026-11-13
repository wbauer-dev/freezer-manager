import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HouseholdDetails } from './household.model';

/** Commands against the household API. Queries are made with httpResource in the pages. */
@Injectable({ providedIn: 'root' })
export class HouseholdApi {
  private readonly http = inject(HttpClient);

  foundHousehold(name: string): Observable<HouseholdDetails> {
    return this.http.post<HouseholdDetails>('/api/households', { name });
  }
}
