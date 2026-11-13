import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';

import { DATE_FORMAT } from '../shared/dates';
import { formatLabelCode } from '../shared/label-code';
import { problemMessage } from '../shared/problem-message';
import { LabelingApi } from './labeling-api';
import { LAYOUT_NAMES, LabelSheet, LabelStock, StickerLayout } from './labeling.model';
import { plannedPositions } from './sheet-preview';

/**
 * The print queue and the household's current A4 sticker sheet.
 */
@Component({
  selector: 'app-labels-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './labels-page.html',
  styleUrl: './labels-page.scss',
})
export class LabelsPage {
  readonly householdId = input.required<string>();

  private readonly api = inject(LabelingApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly sheet = httpResource<LabelSheet>(
    () => `/api/households/${this.householdId()}/label-sheet`,
  );
  protected readonly stock = httpResource<LabelStock>(
    () => `/api/households/${this.householdId()}/labels/stock`,
  );
  protected readonly busy = signal(false);
  protected readonly persistentCount = signal(6);
  protected readonly layoutNames = LAYOUT_NAMES;
  protected readonly dateFormat = DATE_FORMAT;
  protected readonly format = formatLabelCode;

  private readonly current = computed(() => (this.sheet.hasValue() ? this.sheet.value() : null));

  /** Every position of the sheet with its state, for the preview. */
  protected readonly cells = computed(() => {
    const sheet = this.current();
    if (!sheet) {
      return [];
    }
    const planned = plannedPositions(
      sheet.columns * sheet.rows,
      sheet.usedPositions,
      sheet.queue.length,
    );
    return Array.from({ length: sheet.columns * sheet.rows }, (_, position) => {
      const queueIndex = planned.onCurrentSheet.indexOf(position);
      return {
        position,
        used: sheet.usedPositions.includes(position),
        queued: queueIndex >= 0 ? queueIndex + 1 : null,
      };
    });
  });

  protected readonly onNewSheets = computed(() => {
    const sheet = this.current();
    return sheet
      ? plannedPositions(sheet.columns * sheet.rows, sheet.usedPositions, sheet.queue.length)
          .onNewSheets
      : 0;
  });

  protected readonly startHint = computed(() => {
    const sheet = this.current();
    if (!sheet) {
      return '';
    }
    const position = sheet.nextPosition + 1;
    const more = this.onNewSheets();
    return more > 0
      ? $localize`:@@labels.startHint.continues:Starts at position ${position}:position: of your current sheet and continues on a new sheet (${more}:more: more).`
      : $localize`:@@labels.startHint:Starts at position ${position}:position: of your current sheet.`;
  });

  protected removeFromQueueLabel(name: string): string {
    return $localize`:@@labels.removeFromQueue:Remove ${name}:name: from the queue`;
  }

  protected cellTooltip(cell: { used: boolean; queued: number | null }): string {
    return cell.used
      ? $localize`:@@labels.cell.usedTooltip:Used — tap to mark as free`
      : cell.queued
        ? $localize`:@@labels.cell.queuedTooltip:Label ${cell.queued}:number: goes here`
        : $localize`:@@labels.cell.freeTooltip:Free — tap to mark as used`;
  }

  protected cellLabel(cell: { position: number; used: boolean; queued: number | null }): string {
    const position = cell.position + 1;
    return cell.used
      ? $localize`:@@labels.cell.used:Position ${position}:position:, used`
      : cell.queued
        ? $localize`:@@labels.cell.next:Position ${position}:position:, next label`
        : $localize`:@@labels.cell.free:Position ${position}:position:, free`;
  }

  protected print(): void {
    this.busy.set(true);
    this.api.print(this.householdId()).subscribe({
      next: (pdf) => {
        this.busy.set(false);
        download(pdf, 'labels.pdf');
        this.sheet.reload();
        this.snackBar.open(
          $localize`:@@labels.ready:Labels ready — print the PDF at 100 % (actual size)`,
          $localize`:@@common.ok:OK`,
          { duration: 8000 },
        );
      },
      error: (error) => this.failed(error),
    });
  }

  protected remove(code: string, layout: StickerLayout): void {
    this.run(this.api.dequeue(this.householdId(), code, layout));
  }

  protected toggle(position: number): void {
    this.run(this.api.togglePosition(this.householdId(), position));
  }

  protected startNewSheet(): void {
    this.run(
      this.api.startNewSheet(this.householdId()),
      $localize`:@@labels.newSheetStarted:New sheet — printing starts top left`,
    );
  }

  protected changePersistentCount(delta: number): void {
    this.persistentCount.update((count) => Math.min(Math.max(count + delta, 1), 48));
  }

  protected createPersistentLabels(): void {
    const count = this.persistentCount();
    this.run(
      this.api.createPersistentLabels(this.householdId(), count),
      count === 1
        ? $localize`:@@labels.persistentAdded.one:1 persistent label added to the print queue`
        : $localize`:@@labels.persistentAdded.many:${count}:count: persistent labels added to the print queue`,
    );
  }

  private run(command: Observable<unknown>, success?: string): void {
    this.busy.set(true);
    command.subscribe({
      next: () => {
        this.busy.set(false);
        this.sheet.reload();
        this.stock.reload();
        if (success) {
          this.snackBar.open(success, undefined, { duration: 3000 });
        }
      },
      error: (error) => this.failed(error),
    });
  }

  private failed(error: unknown): void {
    this.busy.set(false);
    this.sheet.reload();
    this.snackBar.open(
      problemMessage(error, $localize`:@@common.error.retry:That did not work. Please try again.`),
      $localize`:@@common.ok:OK`,
      { duration: 6000 },
    );
  }
}

function download(file: Blob, name: string): void {
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
