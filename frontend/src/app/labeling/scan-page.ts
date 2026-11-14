import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { formatLabelCode } from '../shared/label-code';
import { LabelingApi } from './labeling-api';
import { ScannedLabel } from './labeling.model';

/**
 * Where a scanned QR code leads (/s/K7Q2M9XA): to the item the label is on, to the freeze page for a free label, or to
 * an explanation.
 */
@Component({
  selector: 'app-scan-page',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <section class="scan glass rise">
      @switch (state()) {
        @case ('resolving') {
          <div class="icon pulse"><mat-icon>qr_code_scanner</mat-icon></div>
          <h1 i18n="@@scan.reading">Reading label {{ format(code()) }}…</h1>
        }
        @case ('used-up') {
          <div class="icon"><mat-icon>task_alt</mat-icon></div>
          <h1 i18n="@@scan.usedUp.title">This label has been used</h1>
          <p i18n="@@scan.usedUp.text">
            The item it was on is already eaten or discarded. One-time labels can’t be used again.
          </p>
          @if (label(); as label) {
            <a
              mat-stroked-button
              [routerLink]="['/households', label.householdId, 'items', label.itemId]"
              ><span i18n="@@scan.usedUp.history">Show the item’s history</span></a
            >
          }
        }
        @case ('unknown') {
          <div class="icon"><mat-icon>help_outline</mat-icon></div>
          <h1 i18n="@@scan.unknown.title">Unknown label</h1>
          <p i18n="@@scan.unknown.text">
            {{ format(code()) }} doesn’t belong to any of your households.
          </p>
          <a mat-stroked-button routerLink="/households" i18n="@@households.title"
            >Your households</a
          >
        }
        @default {
          <div class="icon"><mat-icon>error_outline</mat-icon></div>
          <h1 i18n="@@scan.error">The label could not be read</h1>
          <a mat-stroked-button routerLink="/households" i18n="@@households.title"
            >Your households</a
          >
        }
      }
    </section>
  `,
  styles: `
    .scan {
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
export class ScanPage {
  readonly code = input.required<string>();

  private readonly api = inject(LabelingApi);
  private readonly router = inject(Router);

  protected readonly state = signal<'resolving' | 'used-up' | 'unknown' | 'error'>('resolving');
  protected readonly label = signal<ScannedLabel | null>(null);
  protected readonly format = formatLabelCode;

  constructor() {
    effect(() => {
      const code = this.code();
      untracked(() => this.resolve(code));
    });
  }

  private resolve(code: string): void {
    this.state.set('resolving');
    this.api.scan(code).subscribe({
      next: (label) => {
        if (label.state === 'ATTACHED' && label.itemId) {
          this.router.navigate(['/households', label.householdId, 'items', label.itemId], {
            replaceUrl: true,
          });
        } else if (label.state === 'FREE') {
          this.router.navigate(['/households', label.householdId, 'freeze'], {
            queryParams: { label: label.code },
            replaceUrl: true,
          });
        } else {
          this.label.set(label);
          this.state.set('used-up');
        }
      },
      error: (error) =>
        this.state.set(
          error instanceof HttpErrorResponse && error.status === 404 ? 'unknown' : 'error',
        ),
    });
  }
}
