import { Component, LOCALE_ID, computed, inject, input, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatRippleModule } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, finalize } from 'rxjs';

import { Autofocus } from '../shared/autofocus';
import { EMOJI_SUGGESTIONS, foodHue } from '../shared/food';
import { problemMessage } from '../shared/problem-message';
import { CatalogApi } from './catalog-api';
import {
  CatalogInfo,
  CatalogLanguage,
  FoodCategory,
  MAX_SHELF_LIFE_MONTHS,
  Product,
  ResetMode,
} from './catalog.model';

interface CategoryDraft {
  /** null for a new category. */
  id: string | null;
  name: string;
  emoji: string;
  shelfLifeMonths: number;
}

const NEW_CATEGORY: CategoryDraft = { id: null, name: '', emoji: '🍲', shelfLifeMonths: 6 };

/**
 * The household's foods: categories with their shelf life, and the products in them. Every member may change
 * them, or start over with an empty catalog or the system defaults.
 */
@Component({
  selector: 'app-catalog-page',
  imports: [
    Autofocus,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatRippleModule,
    MatTooltipModule,
    NgTemplateOutlet,
  ],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.scss',
})
export class CatalogPage {
  readonly householdId = input.required<string>();

  private readonly api = inject(CatalogApi);
  private readonly snackBar = inject(MatSnackBar);
  private readonly language = inject(LOCALE_ID);

  protected readonly categories = httpResource<FoodCategory[]>(
    () => `/api/households/${this.householdId()}/food-categories`,
  );
  protected readonly products = httpResource<Product[]>(
    () => `/api/households/${this.householdId()}/products`,
  );
  protected readonly info = httpResource<CatalogInfo>(
    () => `/api/households/${this.householdId()}/catalog`,
  );
  protected readonly languages = httpResource<CatalogLanguage[]>(() => '/api/catalog/languages');

  protected readonly maxShelfLife = MAX_SHELF_LIFE_MONTHS;
  protected readonly emojiSuggestions = EMOJI_SUGGESTIONS;
  protected readonly hue = foodHue;

  protected readonly search = signal('');

  /** Categories with their products, narrowed down by the search. */
  protected readonly sections = computed(() => {
    const categories = this.categories.hasValue() ? this.categories.value() : [];
    const products = this.products.hasValue() ? this.products.value() : [];
    const term = normalize(this.search());
    return categories
      .map((category) => {
        const all = products.filter((product) => product.categoryId === category.id);
        const categoryMatches = !term || normalize(category.name).includes(term);
        const shown = categoryMatches
          ? all
          : all.filter((product) => normalize(product.name).includes(term));
        return { category, all, shown };
      })
      .filter(
        (section) =>
          !term || section.shown.length > 0 || normalize(section.category.name).includes(term),
      );
  });
  protected readonly categoryCount = computed(() =>
    this.categories.hasValue() ? this.categories.value().length : 0,
  );
  protected readonly productCount = computed(() =>
    this.products.hasValue() ? this.products.value().length : 0,
  );
  protected readonly loaded = computed(
    () => this.categories.hasValue() && this.products.hasValue(),
  );

  /** The category being edited or created; one at a time. */
  protected readonly draft = signal<CategoryDraft | null>(null);
  protected readonly draftError = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly renaming = signal<string | null>(null);
  protected readonly confirmingRemoval = signal<string | null>(null);

  protected readonly resetMode = signal<ResetMode>('DEFAULTS');
  protected readonly resetLanguage = signal(this.language);
  protected readonly confirmingReset = signal(false);

  /** Commands in flight. The same command again — a second Enter, a double click — is ignored meanwhile. */
  private readonly inFlight = new Set<string>();

  protected startNewCategory(): void {
    this.draft.set({ ...NEW_CATEGORY });
    this.draftError.set(null);
  }

  protected edit(category: FoodCategory): void {
    this.draft.set({ ...category });
    this.draftError.set(null);
    this.confirmingRemoval.set(null);
  }

  protected cancelEditing(): void {
    this.draft.set(null);
  }

  protected changeDraft(changes: Partial<CategoryDraft>): void {
    this.draft.update((draft) => (draft ? { ...draft, ...changes } : draft));
  }

  protected stepShelfLife(delta: number): void {
    this.draft.update((draft) =>
      draft
        ? {
            ...draft,
            shelfLifeMonths: Math.min(
              this.maxShelfLife,
              Math.max(1, draft.shelfLifeMonths + delta),
            ),
          }
        : draft,
    );
  }

  protected saveDraft(): void {
    const draft = this.draft();
    if (!draft) {
      return;
    }
    const name = draft.name.trim();
    const emoji = draft.emoji.trim();
    if (!name) {
      this.draftError.set($localize`:@@catalog.category.nameRequired:Please enter a name.`);
      return;
    }
    if (!emoji) {
      this.draftError.set($localize`:@@catalog.category.emojiRequired:Please choose an emoji.`);
      return;
    }
    const category = { name, emoji, shelfLifeMonths: draft.shelfLifeMonths };
    const save: Observable<unknown> = draft.id
      ? this.api.editCategory(this.householdId(), { id: draft.id, ...category })
      : this.api.defineCategory(this.householdId(), category);
    this.saving.set(true);
    save.subscribe({
      next: () => {
        this.saving.set(false);
        this.draft.set(null);
        this.categories.reload();
      },
      error: (error) => {
        this.saving.set(false);
        this.draftError.set(
          problemMessage(
            error,
            $localize`:@@common.error.retry:That did not work. Please try again.`,
          ),
        );
      },
    });
  }

  protected removeCategory(category: FoodCategory): void {
    this.confirmingRemoval.set(null);
    this.draft.set(null);
    this.run(
      `remove-category:${category.id}`,
      this.api.removeCategory(this.householdId(), category.id),
      () =>
        this.snackBar.open(
          $localize`:@@catalog.category.removed:${category.name}:name: removed`,
          undefined,
          {
            duration: 3000,
          },
        ),
    );
  }

  protected addProduct(category: FoodCategory, input: HTMLInputElement): void {
    const name = input.value.trim();
    if (name) {
      this.run(
        `new-food:${normalize(name)}`,
        this.api.defineProduct(this.householdId(), name, category.id),
        () => {
          // Keep what was typed in the meantime.
          if (input.value.trim() === name) {
            input.value = '';
          }
        },
      );
    }
  }

  /**
   * Enter and leaving the field save the new name, Escape ends without saving. Ending removes the field, which may
   * blur it once more: only what ended the rename counts.
   */
  protected rename(product: Product, input: HTMLInputElement): void {
    if (this.renaming() !== product.id) {
      return;
    }
    this.renaming.set(null);
    const name = input.value.trim();
    if (name && name !== product.name) {
      this.run(
        `rename:${product.id}`,
        this.api.editProduct(this.householdId(), { ...product, name }),
      );
    }
  }

  protected moveTo(product: Product, category: FoodCategory): void {
    this.run(
      `move:${product.id}`,
      this.api.editProduct(this.householdId(), { ...product, categoryId: category.id }),
      () =>
        this.snackBar.open(
          $localize`:@@catalog.product.moved:${product.name}:product: moved to ${category.name}:category:`,
          undefined,
          { duration: 3000 },
        ),
    );
  }

  protected removeProduct(product: Product): void {
    this.run(
      `remove-food:${product.id}`,
      this.api.removeProduct(this.householdId(), product.id),
      () =>
        this.snackBar.open(
          $localize`:@@catalog.product.removed:${product.name}:name: removed`,
          undefined,
          {
            duration: 3000,
          },
        ),
    );
  }

  protected reset(): void {
    this.confirmingReset.set(false);
    const mode = this.resetMode();
    this.run(
      'reset',
      this.api.reset(this.householdId(), mode, mode === 'DEFAULTS' ? this.resetLanguage() : null),
      () => {
        this.info.reload();
        this.snackBar.open(
          mode === 'EMPTY'
            ? $localize`:@@catalog.reset.doneEmpty:Your catalog is empty now.`
            : $localize`:@@catalog.reset.doneDefaults:Your catalog starts over with the defaults.`,
          undefined,
          { duration: 4000 },
        );
      },
    );
  }

  protected languageName(code: string | null): string {
    const languages = this.languages.hasValue() ? this.languages.value() : [];
    return languages.find((language) => language.code === code)?.name ?? code ?? '';
  }

  protected editLabel(category: FoodCategory): string {
    return $localize`:@@catalog.category.editLabel:Edit ${category.name}:name:`;
  }

  protected productMenuLabel(product: Product): string {
    return $localize`:@@catalog.product.menuLabel:Change ${product.name}:name:`;
  }

  protected addLabel(category: FoodCategory): string {
    return $localize`:@@catalog.product.addLabel:New food in ${category.name}:name:`;
  }

  /** Runs a command unless the same one (by key) is still in flight, then shows the catalog as it is now. */
  private run(key: string, command: Observable<unknown>, onSuccess?: () => void): void {
    if (this.inFlight.has(key)) {
      return;
    }
    this.inFlight.add(key);
    command.pipe(finalize(() => this.inFlight.delete(key))).subscribe({
      next: () => {
        onSuccess?.();
        this.categories.reload();
        this.products.reload();
      },
      error: (error) => {
        this.categories.reload();
        this.products.reload();
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

function normalize(text: string): string {
  return text.trim().toLocaleLowerCase();
}
