import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, map, of } from 'rxjs';

import { HouseholdSummary } from './household.model';

/**
 * The start page: users who belong to exactly one household go straight into it; everyone else picks one (or founds
 * the first) on the households page, which stays reachable through the household's back button.
 */
export const startPageGuard: CanActivateFn = () => {
  // Guards run side by side: without a session, authGuard sends the user to the login and back here afterwards.
  if (!inject(OidcSecurityService).authenticated().isAuthenticated) {
    return true;
  }
  const router = inject(Router);
  const households = router.createUrlTree(['/households']);
  return inject(HttpClient)
    .get<HouseholdSummary[]>('/api/households')
    .pipe(
      map((list) =>
        list.length === 1 ? router.createUrlTree(['/households', list[0].id]) : households,
      ),
      // The households page shows why loading failed.
      catchError(() => of(households)),
    );
};
