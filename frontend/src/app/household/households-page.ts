import { Component, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormField, form, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRippleModule } from '@angular/material/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';

import { InitialsAvatar } from '../shared/initials-avatar';
import { HouseholdApi } from './household-api';
import { HouseholdSummary, MAX_HOUSEHOLD_NAME_LENGTH } from './household.model';

@Component({
  selector: 'app-households-page',
  imports: [
    RouterLink,
    FormField,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRippleModule,
    InitialsAvatar,
  ],
  templateUrl: './households-page.html',
  styleUrl: './households-page.scss',
})
export class HouseholdsPage {
  private readonly api = inject(HouseholdApi);
  private readonly router = inject(Router);
  private readonly auth = inject(OidcSecurityService);

  protected readonly households = httpResource<HouseholdSummary[]>(() => '/api/households');

  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    const time =
      hour < 5
        ? $localize`:@@households.greeting.night:Good night`
        : hour < 12
          ? $localize`:@@households.greeting.morning:Good morning`
          : hour < 18
            ? $localize`:@@households.greeting.afternoon:Good afternoon`
            : $localize`:@@households.greeting.evening:Good evening`;
    const user = this.auth.userData().userData;
    const firstName = (user?.given_name ?? user?.name ?? '').split(' ')[0];
    return firstName
      ? $localize`:@@households.greeting.withName:${time}:greeting:, ${firstName}:name:`
      : time;
  });

  protected readonly foundModel = signal({ name: '' });
  protected readonly foundForm = form(this.foundModel, (household) => {
    required(household.name, {
      message: $localize`:@@common.validation.nameRequired:Please enter a name.`,
    });
    maxLength(household.name, MAX_HOUSEHOLD_NAME_LENGTH, {
      message: $localize`:@@common.validation.maxLength:At most ${MAX_HOUSEHOLD_NAME_LENGTH}:max: characters.`,
    });
  });

  protected async foundHousehold(event: Event): Promise<void> {
    event.preventDefault();
    await submit(this.foundForm, async (fields) => {
      try {
        const household = await firstValueFrom(this.api.foundHousehold(fields.name().value()));
        await this.router.navigate(['/households', household.id]);
        return undefined;
      } catch {
        return {
          kind: 'server',
          message: $localize`:@@households.found.error:The household could not be created. Please try again.`,
        };
      }
    });
  }
}
