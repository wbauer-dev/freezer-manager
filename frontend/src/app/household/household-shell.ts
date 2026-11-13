import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { filter, map } from 'rxjs';

import { InitialsAvatar } from '../shared/initials-avatar';
import { HouseholdDetails } from './household.model';

/**
 * Frame for everything inside one household: its name, the main sections and the "Freeze" action.
 */
@Component({
  selector: 'app-household-shell',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatButtonModule,
    MatIconModule,
    MatRippleModule,
    InitialsAvatar,
  ],
  templateUrl: './household-shell.html',
  styleUrl: './household-shell.scss',
})
export class HouseholdShell {
  readonly householdId = input.required<string>();

  private readonly router = inject(Router);

  protected readonly household = httpResource<HouseholdDetails>(
    () => `/api/households/${this.householdId()}`,
  );

  protected readonly sections = [
    {
      path: 'eat-first',
      label: $localize`:@@household.section.eatFirst:Eat first`,
      icon: 'hourglass_bottom',
    },
    { path: 'freezers', label: $localize`:@@household.section.freezers:Freezers`, icon: 'kitchen' },
    { path: 'catalog', label: $localize`:@@household.section.foods:Foods`, icon: 'nutrition' },
    { path: 'labels', label: $localize`:@@household.section.labels:Labels`, icon: 'qr_code_2' },
    { path: 'members', label: $localize`:@@household.section.members:Members`, icon: 'group' },
  ];

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected readonly onFreezePage = computed(() => this.url().endsWith('/freeze'));
  /** The floating button (phones) only appears on list pages, never over an item's own actions. */
  protected readonly onListPage = computed(() =>
    /\/(eat-first|freezers|catalog|labels|members)$/.test(this.url()),
  );

  protected notFound(): boolean {
    const error = this.household.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  }
}
