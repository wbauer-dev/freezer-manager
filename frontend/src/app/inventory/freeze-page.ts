import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormField, form, max, maxLength, min, required, submit } from '@angular/forms/signals';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRippleModule } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { CatalogApi } from '../catalog/catalog-api';
import { emojiByProduct } from '../catalog/catalog-lookup';
import { FoodCategory, Product } from '../catalog/catalog.model';
import { LabelingApi } from '../labeling/labeling-api';
import { FreeLabel, LabelStock } from '../labeling/labeling.model';
import { MediaApi } from '../media/media-api';
import { PhotoLoader } from '../media/photo-loader';
import { preparePhoto } from '../media/prepare-photo';
import { DATE_FORMAT, addMonths, today } from '../shared/dates';
import { formatLabelCode, normalizeLabelCode } from '../shared/label-code';
import { FoodAvatar } from '../shared/food-avatar';
import { LoadError, reloadFailed } from '../shared/load-error';
import { problemMessage } from '../shared/problem-message';
import { InventoryApi } from './inventory-api';
import { Freezer } from './inventory.model';

interface FreezeModel {
  product: string;
  categoryId: string;
  compartmentId: string;
  portions: number;
  gramsPerPortion: number | null;
  frozenOn: string;
  bestBefore: string;
  note: string;
  labelMode: 'new' | 'existing';
  labelCode: string;
}

/**
 * Put a new item into a freezer. If the product is not in the household's catalog yet, it is added first —
 * two commands to two bounded contexts, coordinated by the UI.
 */
@Component({
  selector: 'app-freeze-page',
  imports: [
    DatePipe,
    RouterLink,
    FormField,
    MatAutocompleteModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRippleModule,
    FoodAvatar,
    LoadError,
  ],
  templateUrl: './freeze-page.html',
  styleUrl: './freeze-page.scss',
})
export class FreezePage {
  readonly householdId = input.required<string>();
  /** Query parameter: the code of a free label that was just scanned. */
  readonly label = input<string>();

  private readonly catalogApi = inject(CatalogApi);
  private readonly labelingApi = inject(LabelingApi);
  private readonly mediaApi = inject(MediaApi);
  private readonly photos = inject(PhotoLoader);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly products = httpResource<Product[]>(
    () => `/api/households/${this.householdId()}/products`,
  );
  protected readonly categories = httpResource<FoodCategory[]>(
    () => `/api/households/${this.householdId()}/food-categories`,
  );
  protected readonly freezers = httpResource<Freezer[]>(
    () => `/api/households/${this.householdId()}/freezers`,
  );
  protected readonly labelStock = httpResource<LabelStock>(
    () => `/api/households/${this.householdId()}/labels/stock`,
  );
  protected readonly freePersistentLabels = computed(() =>
    this.labelStock.hasValue() ? this.labelStock.value().freePersistent : 0,
  );
  /**
   * The household's free persistent labels to pick from, loaded once "Use a label I have" is chosen. The address is a
   * computed of its own, so typing in the form doesn't load them again.
   */
  private readonly freeLabelsUrl = computed(() =>
    this.model().labelMode === 'existing'
      ? `/api/households/${this.householdId()}/labels/free`
      : undefined,
  );
  private readonly freeLabels = httpResource<FreeLabel[]>(() => this.freeLabelsUrl());
  /** Free labels matching what was typed: part of the code, or of what the label held last. */
  protected readonly labelSuggestions = computed(() => {
    const labels = this.freeLabels.hasValue() ? this.freeLabels.value() : [];
    const text = this.model().labelCode.trim().toLowerCase();
    if (!text) {
      return labels;
    }
    const code = normalizeLabelCode(text);
    return labels.filter(
      (label) =>
        label.code.includes(code) || (label.lastContents?.toLowerCase().includes(text) ?? false),
    );
  });
  protected readonly formatCode = formatLabelCode;
  protected readonly dateFormat = DATE_FORMAT;
  protected readonly portionCount = computed(() => this.model().portions || 1);
  /** For a persistent label: also print a sticker that shows what is inside. */
  protected readonly printContents = signal(false);
  private labelModeChosen = false;

  protected readonly model = signal<FreezeModel>({
    product: '',
    categoryId: '',
    compartmentId: '',
    portions: 1,
    gramsPerPortion: null,
    frozenOn: today(),
    bestBefore: '',
    note: '',
    labelMode: 'new',
    labelCode: '',
  });

  /** Uploaded right away when chosen; the item only keeps its id. */
  protected readonly photo = signal<{ id: string; preview: string } | null>(null);
  protected readonly uploadingPhoto = signal(false);
  protected readonly photoError = signal<string | null>(null);
  /** Counts the photos chosen and removed, so that an upload that was overtaken can't bring its photo back. */
  private photoChanges = 0;

  private readonly productList = computed(() =>
    this.products.hasValue() ? this.products.value() : [],
  );
  protected readonly categoryList = computed(() =>
    this.categories.hasValue() ? this.categories.value() : [],
  );

  protected readonly matchingProduct = computed(() => {
    const name = this.model().product.trim().toLowerCase();
    return this.productList().find((product) => product.name.toLowerCase() === name);
  });
  protected readonly isNewProduct = computed(
    () => this.model().product.trim() !== '' && !this.matchingProduct(),
  );
  protected readonly suggestions = computed(() => {
    const text = this.model().product.trim().toLowerCase();
    return this.productList()
      .filter((product) => product.name.toLowerCase().includes(text))
      .slice(0, 8);
  });
  protected readonly quickPicks = computed(() => this.productList().slice(0, 10));

  /** The category that determines the recommended shelf life. */
  protected readonly category = computed(() => {
    const categoryId = this.matchingProduct()?.categoryId ?? this.model().categoryId;
    return this.categoryList().find((category) => category.id === categoryId);
  });

  private readonly locations = computed(() =>
    (this.freezers.hasValue() ? this.freezers.value() : []).flatMap((freezer) =>
      freezer.compartments.map((compartment) => ({
        freezerId: freezer.id,
        compartmentId: compartment.id,
        label: `${freezer.name} · ${compartment.name}`,
      })),
    ),
  );
  protected readonly location = computed(() =>
    this.locations().find((location) => location.compartmentId === this.model().compartmentId),
  );

  /** Preview only — the backend calculates the real date with the same rule. */
  protected readonly previewBestBefore = computed(() => {
    const { bestBefore, frozenOn } = this.model();
    if (bestBefore) {
      return bestBefore;
    }
    const category = this.category();
    return category && frozenOn ? addMonths(frozenOn, category.shelfLifeMonths) : null;
  });

  protected readonly freezeForm = form(this.model, (item) => {
    required(item.product, {
      message: $localize`:@@freeze.validation.product:What are you freezing?`,
    });
    maxLength(item.product, 40, {
      message: $localize`:@@common.validation.maxLength:At most ${40}:max: characters.`,
    });
    required(item.categoryId, {
      message: $localize`:@@freeze.validation.category:Choose a category for the new product.`,
      when: () => this.isNewProduct(),
    });
    required(item.compartmentId, {
      message: $localize`:@@freeze.validation.compartment:Where are you putting it?`,
    });
    min(item.portions, 1, {
      message: $localize`:@@freeze.validation.minPortions:At least 1 portion.`,
    });
    max(item.portions, 99, {
      message: $localize`:@@freeze.validation.maxPortions:At most 99 portions.`,
    });
    min(item.gramsPerPortion, 1, {
      message: $localize`:@@freeze.validation.minGrams:At least 1 g.`,
    });
    max(item.gramsPerPortion, 20000, {
      message: $localize`:@@freeze.validation.maxGrams:At most 20,000 g.`,
    });
    required(item.frozenOn, {
      message: $localize`:@@freeze.validation.frozenOn:When was it frozen?`,
    });
    maxLength(item.note, 200, {
      message: $localize`:@@common.validation.maxLength:At most ${200}:max: characters.`,
    });
    required(item.labelCode, {
      message: $localize`:@@freeze.validation.labelCode:Enter the code printed on the label.`,
      when: () => this.model().labelMode === 'existing',
    });
  });

  constructor() {
    // A scanned free label arrives as ?label=CODE.
    effect(() => {
      const label = this.label();
      if (label) {
        this.labelModeChosen = true;
        this.model.update((model) => ({
          ...model,
          labelMode: 'existing',
          labelCode: formatLabelCode(label),
        }));
      }
    });
    // Labels the household already owns come first: no printing needed.
    effect(() => {
      if (this.freePersistentLabels() > 0 && !this.labelModeChosen) {
        this.model.update((model) => ({ ...model, labelMode: 'existing' }));
      }
    });
    // With a single compartment there is nothing to choose.
    effect(() => {
      const locations = this.locations();
      if (locations.length === 1 && this.model().compartmentId === '') {
        this.model.update((model) => ({ ...model, compartmentId: locations[0].compartmentId }));
      }
    });
  }

  private readonly emojis = computed(() => emojiByProduct(this.productList(), this.categoryList()));

  protected emojiOf(product: Product): string {
    return this.emojis().get(product.id) ?? '';
  }

  protected pick(product: Product): void {
    this.model.update((model) => ({ ...model, product: product.name }));
  }

  protected reload(): void {
    reloadFailed(this.freezers, this.products, this.categories);
  }

  protected chooseCategory(categoryId: string): void {
    this.model.update((model) => ({ ...model, categoryId }));
  }

  protected chooseCompartment(compartmentId: string): void {
    this.model.update((model) => ({ ...model, compartmentId }));
  }

  protected async choosePhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const change = ++this.photoChanges;
    const current = () => change === this.photoChanges;
    this.uploadingPhoto.set(true);
    this.photoError.set(null);
    try {
      const prepared = await preparePhoto(file);
      const uploaded = await firstValueFrom(this.mediaApi.upload(this.householdId(), prepared));
      if (current()) {
        this.replacePhoto({
          id: uploaded.id,
          preview: this.photos.remember(uploaded.id, prepared),
        });
      }
    } catch (error) {
      if (current()) {
        this.photoError.set(
          problemMessage(
            error,
            $localize`:@@freeze.photo.uploadError:The photo could not be uploaded.`,
          ),
        );
      }
    } finally {
      if (current()) {
        this.uploadingPhoto.set(false);
      }
    }
  }

  protected removePhoto(): void {
    this.photoChanges++;
    this.uploadingPhoto.set(false);
    this.replacePhoto(null);
  }

  /** The preview of a photo that is replaced or removed is not needed any more. */
  private replacePhoto(photo: { id: string; preview: string } | null): void {
    const previous = this.photo();
    if (previous) {
      this.photos.forget(previous.id);
    }
    this.photo.set(photo);
  }

  protected chooseLabelMode(labelMode: 'new' | 'existing'): void {
    this.labelModeChosen = true;
    this.model.update((model) => ({ ...model, labelMode }));
  }

  protected changePortions(delta: number): void {
    this.model.update((model) => ({
      ...model,
      portions: Math.min(Math.max((model.portions || 0) + delta, 1), 99),
    }));
  }

  protected async freeze(event: Event): Promise<void> {
    event.preventDefault();
    if (this.uploadingPhoto()) {
      // The Freeze button waits for the photo; this also holds for Enter in a field.
      return;
    }
    await submit(this.freezeForm, async (fields) => {
      const value = fields().value();
      const location = this.locations().find((l) => l.compartmentId === value.compartmentId)!;
      try {
        const labelCode =
          value.labelMode === 'existing' ? await this.checkLabel(value.labelCode) : null;
        const productId = this.matchingProduct()?.id ?? (await this.defineProduct(value));
        const item = await firstValueFrom(
          this.inventoryApi.freeze(this.householdId(), {
            productId,
            freezerId: location.freezerId,
            compartmentId: location.compartmentId,
            portions: value.portions,
            gramsPerPortion: value.gramsPerPortion || null,
            frozenOn: value.frozenOn,
            bestBefore: value.bestBefore || null,
            note: value.note.trim() || null,
            labelCode,
            photoId: this.photo()?.id ?? null,
          }),
        );
        if (labelCode && this.printContents()) {
          await this.queueContentsSticker(labelCode, item.id);
        }
        await this.router.navigate(['..', 'items', item.id], { relativeTo: this.route });
        return undefined;
      } catch (error) {
        const message =
          error instanceof LabelProblem
            ? error.message
            : problemMessage(
                error,
                $localize`:@@freeze.error:The item could not be frozen. Please try again.`,
              );
        return { kind: 'server', message };
      }
    });
  }

  /**
   * The item is frozen by now, so a sticker that can't be queued must not fail the freezing: freezing again would
   * fail on the label, or freeze the food twice. The sticker can be printed from the item page instead.
   */
  private async queueContentsSticker(labelCode: string, itemId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.labelingApi.enqueue(this.householdId(), labelCode, 'CONTENT_ONLY', itemId),
      );
    } catch {
      this.snackBar.open(
        $localize`:@@freeze.stickerError:Frozen — but the contents sticker could not be added to the print queue. Try again with “Print sticker”.`,
        $localize`:@@common.ok:OK`,
        { duration: 10000 },
      );
    }
  }

  /**
   * A label the member already has must be a free label of this household. Labeling knows; Inventory only makes sure
   * the code is not on another item in the freezer.
   */
  private async checkLabel(text: string): Promise<string> {
    const code = normalizeLabelCode(text);
    try {
      const label = await firstValueFrom(this.labelingApi.scan(code));
      if (label.householdId !== this.householdId()) {
        throw new LabelProblem(
          $localize`:@@freeze.label.otherHousehold:This label belongs to another household.`,
        );
      }
      if (label.state === 'ATTACHED') {
        throw new LabelProblem(
          $localize`:@@freeze.label.inUse:This label is already on another item.`,
        );
      }
      if (label.state === 'USED_UP') {
        throw new LabelProblem(
          $localize`:@@freeze.label.usedUp:This label has already been used. Print a new one instead.`,
        );
      }
      return code;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        throw new LabelProblem(
          $localize`:@@freeze.label.unknown:Unknown label — please check the code.`,
        );
      }
      throw error;
    }
  }

  private async defineProduct(value: FreezeModel): Promise<string> {
    const product = await firstValueFrom(
      this.catalogApi.defineProduct(this.householdId(), value.product.trim(), value.categoryId),
    );
    this.products.reload();
    return product.id;
  }
}

class LabelProblem extends Error {}
