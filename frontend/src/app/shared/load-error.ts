import { Component, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Says what could not be loaded (the content) and offers to try again. */
@Component({
  selector: 'app-load-error',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="load-error glass rise" role="alert">
      <mat-icon aria-hidden="true">cloud_off</mat-icon>
      <p><ng-content /></p>
      <button mat-stroked-button type="button" (click)="retry.emit()">
        <mat-icon>refresh</mat-icon>
        <span i18n="@@loadError.retry">Try again</span>
      </button>
    </div>
  `,
  styles: `
    .load-error {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 28px 24px;
      text-align: center;
      border-radius: var(--radius-xl);
    }
    .mat-icon {
      color: var(--muted);
    }
    p {
      margin: 0 0 4px;
    }
  `,
})
export class LoadError {
  readonly retry = output();
}

/** Loads again those of a page's resources that could not be loaded. */
export function reloadFailed(...resources: { error(): unknown; reload(): unknown }[]): void {
  for (const resource of resources) {
    if (resource.error()) {
      resource.reload();
    }
  }
}
