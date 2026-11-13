import { Component, computed, inject, input, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import {
  CdkDrag,
  CdkDragDrop,
  CdkDragHandle,
  CdkDragPlaceholder,
  CdkDropList,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { FormField, form, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, firstValueFrom } from 'rxjs';

import { problemMessage } from '../shared/problem-message';
import { InventoryApi } from './inventory-api';
import { Compartment, Freezer } from './inventory.model';

const DEFAULT_COMPARTMENTS = $localize`:@@freezers.defaultDrawers:Top drawer, Middle drawer, Bottom drawer`;
const MAX_NAME_LENGTH = 40;

@Component({
  selector: 'app-freezers-page',
  imports: [
    CdkDrag,
    CdkDragHandle,
    CdkDragPlaceholder,
    CdkDropList,
    FormField,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTooltipModule,
  ],
  templateUrl: './freezers-page.html',
  styleUrl: './freezers-page.scss',
})
export class FreezersPage {
  readonly householdId = input.required<string>();

  private readonly api = inject(InventoryApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly freezers = httpResource<Freezer[]>(
    () => `/api/households/${this.householdId()}/freezers`,
  );

  protected readonly setUpModel = signal({ name: '', compartments: DEFAULT_COMPARTMENTS });
  protected readonly setUpForm = form(this.setUpModel, (freezer) => {
    required(freezer.name, {
      message: $localize`:@@common.validation.nameRequired:Please enter a name.`,
    });
    maxLength(freezer.name, MAX_NAME_LENGTH, {
      message: $localize`:@@common.validation.maxLength:At most ${MAX_NAME_LENGTH}:max: characters.`,
    });
    required(freezer.compartments, {
      message: $localize`:@@freezers.drawersRequired:Name at least one drawer.`,
    });
  });

  /** The freezer whose removal is waiting for confirmation. */
  protected readonly confirmingRemoval = signal<string | null>(null);

  /** Live preview of the drawers while typing. */
  protected readonly previewDrawers = computed(() => splitNames(this.setUpModel().compartments));

  protected async setUpFreezer(event: Event): Promise<void> {
    event.preventDefault();
    await submit(this.setUpForm, async (fields) => {
      const value = fields().value();
      const compartments = splitNames(value.compartments);
      try {
        await firstValueFrom(
          this.api.setUpFreezer(this.householdId(), value.name.trim(), compartments),
        );
        this.setUpForm().reset({ name: '', compartments: DEFAULT_COMPARTMENTS });
        this.freezers.reload();
        return undefined;
      } catch (error) {
        return {
          kind: 'server',
          message: problemMessage(
            error,
            $localize`:@@freezers.setUpError:The freezer could not be set up.`,
          ),
        };
      }
    });
  }

  protected addCompartment(freezer: Freezer, input: HTMLInputElement): void {
    const name = input.value.trim();
    if (name) {
      this.run(
        this.api.addCompartment(this.householdId(), freezer.id, name),
        () => (input.value = ''),
      );
    }
  }

  protected removeCompartment(freezer: Freezer, compartment: Compartment): void {
    this.run(this.api.removeCompartment(this.householdId(), freezer.id, compartment.id));
  }

  protected removeFreezer(freezer: Freezer): void {
    this.confirmingRemoval.set(null);
    this.run(this.api.removeFreezer(this.householdId(), freezer.id), () =>
      this.snackBar.open($localize`:@@common.removed:${freezer.name}:name: removed`, undefined, {
        duration: 3000,
      }),
    );
  }

  /** Shows the new order right away, then saves it; on failure the saved order is reloaded. */
  protected rearrange(freezer: Freezer, event: CdkDragDrop<Compartment[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const compartments = [...freezer.compartments];
    moveItemInArray(compartments, event.previousIndex, event.currentIndex);
    this.freezers.update((freezers) =>
      freezers?.map((f) => (f.id === freezer.id ? { ...f, compartments } : f)),
    );
    this.run(
      this.api.rearrangeCompartments(
        this.householdId(),
        freezer.id,
        compartments.map((compartment) => compartment.id),
      ),
      undefined,
      false,
    );
  }

  protected itemCount(freezer: Freezer): number {
    return freezer.compartments.reduce((sum, compartment) => sum + compartment.itemCount, 0);
  }

  protected removeLabel(name: string): string {
    return $localize`:@@common.removeNamed:Remove ${name}:name:`;
  }

  protected freezerTooltip(freezer: Freezer): string {
    return this.itemCount(freezer) > 0
      ? $localize`:@@freezers.onlyEmptyFreezers:Only empty freezers can be removed`
      : this.removeLabel(freezer.name);
  }

  protected drawerTooltip(freezer: Freezer, compartment: Compartment): string {
    return this.canRemove(freezer, compartment)
      ? this.removeLabel(compartment.name)
      : $localize`:@@freezers.onlyEmptyDrawers:Only empty drawers can be removed, and one has to stay`;
  }

  protected newDrawerLabel(freezer: Freezer): string {
    return $localize`:@@freezers.newDrawerIn:New drawer in ${freezer.name}:name:`;
  }

  protected canRemove(freezer: Freezer, compartment: Compartment): boolean {
    return compartment.itemCount === 0 && freezer.compartments.length > 1;
  }

  private run(command: Observable<void>, onSuccess?: () => void, reload = true): void {
    command.subscribe({
      next: () => {
        onSuccess?.();
        if (reload) {
          this.freezers.reload();
        }
      },
      error: (error) => {
        this.freezers.reload();
        this.snackBar.open(
          problemMessage(
            error,
            $localize`:@@common.error.retry:That did not work. Please try again.`,
          ),
          $localize`:@@common.ok:OK`,
          { duration: 6000 },
        );
      },
    });
  }
}

function splitNames(names: string): string[] {
  return names
    .split(',')
    .map((name) => name.trim())
    .filter((name) => name.length > 0);
}
