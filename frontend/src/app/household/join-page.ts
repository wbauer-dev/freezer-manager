import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { problemMessage } from '../shared/problem-message';
import { HouseholdApi } from './household-api';

/**
 * Where an invitation link leads (/join/<code>), after logging in: accepts the invitation and opens the household.
 * Members who open a link again simply land in their household.
 */
@Component({
  selector: 'app-join-page',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <section class="join glass rise">
      @switch (state()) {
        @case ('joining') {
          <div class="icon pulse"><mat-icon>group_add</mat-icon></div>
          <h1 i18n="@@join.joining">Joining the household…</h1>
        }
        @case ('invalid') {
          <div class="icon"><mat-icon>link_off</mat-icon></div>
          <h1 i18n="@@join.invalid.title">This invitation doesn’t work</h1>
          <p>{{ problem() }}</p>
          <a mat-stroked-button routerLink="/households" i18n="@@households.title"
            >Your households</a
          >
        }
        @default {
          <div class="icon"><mat-icon>error_outline</mat-icon></div>
          <h1 i18n="@@join.error.title">You could not join the household</h1>
          <p>{{ problem() }}</p>
          <button mat-flat-button class="cta" type="button" (click)="accept(code())">
            <mat-icon>refresh</mat-icon>
            <span i18n="@@join.error.retry">Try again</span>
          </button>
        }
      }
    </section>
  `,
  styles: `
    .join {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      max-width: 440px;
      margin: 40px auto;
      padding: 32px 24px;
      text-align: center;
    }
    .icon {
      display: grid;
      place-items: center;
      width: 64px;
      height: 64px;
      border-radius: 20px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
      .mat-icon {
        font-size: 34px;
        width: 34px;
        height: 34px;
      }
    }
    .pulse {
      animation: pulse 1.2s ease-in-out infinite;
    }
    @keyframes pulse {
      50% {
        transform: scale(0.92);
      }
    }
    h1 {
      margin: 8px 0 0;
      font-size: 1.4rem;
    }
    p {
      margin: 0 0 8px;
      color: var(--muted);
    }
  `,
})
export class JoinPage {
  readonly code = input.required<string>();

  private readonly api = inject(HouseholdApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly state = signal<'joining' | 'invalid' | 'error'>('joining');
  protected readonly problem = signal('');

  constructor() {
    effect(() => {
      const code = this.code();
      untracked(() => this.accept(code));
    });
  }

  protected accept(code: string): void {
    this.state.set('joining');
    this.api.acceptInvitation(code).subscribe({
      next: (household) => {
        this.snackBar.open(
          $localize`:@@join.welcome:You’re a member of ${household.name}:name:`,
          undefined,
          { duration: 4000 },
        );
        this.router.navigate(['/households', household.id], { replaceUrl: true });
      },
      error: (error) => {
        // Unknown, expired, used and revoked invitations all get a 404 with the same explanation.
        const invalid = error instanceof HttpErrorResponse && error.status === 404;
        this.problem.set(
          problemMessage(
            error,
            invalid
              ? $localize`:@@join.invalid.text:Invitations work once and expire after 7 days. Ask someone in the household for a new link.`
              : $localize`:@@common.error.retry:That did not work. Please try again.`,
          ),
        );
        this.state.set(invalid ? 'invalid' : 'error');
      },
    });
  }
}
