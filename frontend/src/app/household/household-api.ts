import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HouseholdDetails, HouseholdSummary, Invitation } from './household.model';

/** Commands against the household API. Queries are made with httpResource in the pages. */
@Injectable({ providedIn: 'root' })
export class HouseholdApi {
  private readonly http = inject(HttpClient);

  foundHousehold(name: string): Observable<HouseholdDetails> {
    return this.http.post<HouseholdDetails>('/api/households', { name });
  }

  /** Owners only: a new single-use link, valid for 7 days. */
  invite(householdId: string): Observable<Invitation> {
    return this.http.post<Invitation>(`/api/households/${householdId}/invitations`, null);
  }

  revokeInvitation(householdId: string, code: string): Observable<void> {
    return this.http.delete<void>(
      `/api/households/${householdId}/invitations/${encodeURIComponent(code)}`,
    );
  }

  /** Joins the household the invitation is for; members simply stay members. */
  acceptInvitation(code: string): Observable<HouseholdSummary> {
    return this.http.post<HouseholdSummary>(
      `/api/invitations/${encodeURIComponent(code)}/accept`,
      null,
    );
  }

  /** Owners only. Answers with the household as it is afterwards. */
  makeOwner(householdId: string, userId: string): Observable<HouseholdDetails> {
    return this.http.post<HouseholdDetails>(`${this.member(householdId, userId)}/make-owner`, null);
  }

  /** Owners only. Answers with the household as it is afterwards. */
  removeMember(householdId: string, userId: string): Observable<HouseholdDetails> {
    return this.http.delete<HouseholdDetails>(this.member(householdId, userId));
  }

  leave(householdId: string): Observable<void> {
    return this.http.post<void>(`/api/households/${householdId}/leave`, null);
  }

  private member(householdId: string, userId: string): string {
    return `/api/households/${householdId}/members/${encodeURIComponent(userId)}`;
  }
}
