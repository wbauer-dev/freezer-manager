import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';

import { InitialsAvatar } from '../shared/initials-avatar';
import { HouseholdDetails } from './household.model';

@Component({
  selector: 'app-members-page',
  imports: [DatePipe, MatIconModule, InitialsAvatar],
  template: `
    @if (household.isLoading()) {
      @for (i of [0, 1]; track i) {
        <div class="skeleton row-skeleton"></div>
      }
    }
    @if (household.hasValue()) {
      <div class="members">
        @for (member of household.value().members; track member.userId) {
          <div class="member glass rise" [style.--i]="$index">
            <app-initials-avatar [name]="member.displayName" [size]="48" />
            <div class="info">
              <span class="name">{{ member.displayName }}</span>
              <span class="since" i18n="@@members.since"
                >since {{ member.joinedAt | date: 'mediumDate' }}</span
              >
            </div>
            <span class="role" [class.owner]="member.role === 'OWNER'">
              <mat-icon>{{ member.role === 'OWNER' ? 'crown' : 'person' }}</mat-icon>
              @if (member.role === 'OWNER') {
                <span i18n="@@members.owner">Owner</span>
              } @else {
                <span i18n="@@members.member">Member</span>
              }
            </span>
          </div>
        }
      </div>
      <p class="founded rise">
        <mat-icon>cake</mat-icon>
        <span i18n="@@members.founded"
          >Household founded {{ household.value().foundedAt | date: 'longDate' }}</span
        >
      </p>
    }
  `,
  styles: `
    .members {
      display: grid;
      gap: 12px;
    }
    .row-skeleton {
      height: 76px;
      margin-bottom: 12px;
    }
    .member {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 16px;
    }
    .info {
      display: flex;
      flex-direction: column;
      flex: 1;
      min-width: 0;
    }
    .name {
      font-family: var(--font-display);
      font-size: 1.1rem;
      font-weight: 600;
    }
    .since {
      color: var(--muted);
      font: var(--mat-sys-body-small);
    }
    .role {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 12px 4px 8px;
      border-radius: 999px;
      background: var(--hairline);
      font: var(--mat-sys-label-medium);
      .mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
      &.owner {
        background: var(--soon-bg);
        color: var(--soon);
        .mat-icon {
          font-variation-settings: 'FILL' 1;
        }
      }
    }
    .founded {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 20px 4px;
      color: var(--muted);
      .mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
    }
  `,
})
export class MembersPage {
  readonly householdId = input.required<string>();

  protected readonly household = httpResource<HouseholdDetails>(
    () => `/api/households/${this.householdId()}`,
  );
}
