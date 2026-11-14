import { Component, computed, inject, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, finalize } from 'rxjs';

import { emojiByProduct } from '../catalog/catalog-lookup';
import { FoodCategory, Product } from '../catalog/catalog.model';
import { problemMessage } from '../shared/problem-message';
import { ShoppingApi } from './shopping-api';
import {
  AddEntryRequest,
  MAX_NAME_LENGTH,
  MAX_QUANTITY_LENGTH,
  ShoppingEntry,
  ShoppingList,
} from './shopping.model';

/**
 * The household's shared shopping list: add what you need, tick it off in the shop, clear the basket afterwards.
 * Products come from the household's catalog; anything else is added as free text.
 */
@Component({
  selector: 'app-shopping-page',
  imports: [
    NgTemplateOutlet,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTooltipModule,
  ],
  templateUrl: './shopping-page.html',
  styleUrl: './shopping-page.scss',
})
export class ShoppingPage {
  readonly householdId = input.required<string>();

  private readonly api = inject(ShoppingApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly list = httpResource<ShoppingList>(
    () => `/api/households/${this.householdId()}/shopping-list`,
  );
  protected readonly products = httpResource<Product[]>(
    () => `/api/households/${this.householdId()}/products`,
  );
  protected readonly categories = httpResource<FoodCategory[]>(
    () => `/api/households/${this.householdId()}/food-categories`,
  );

  protected readonly food = signal('');
  protected readonly quantity = signal('');
  protected readonly adding = signal(false);
  protected readonly maxName = MAX_NAME_LENGTH;
  protected readonly maxQuantity = MAX_QUANTITY_LENGTH;

  private readonly productList = computed(() =>
    this.products.hasValue() ? this.products.value() : [],
  );
  private readonly emojis = computed(() =>
    emojiByProduct(this.productList(), this.categories.hasValue() ? this.categories.value() : []),
  );

  private readonly entries = computed(() =>
    this.list.hasValue() ? this.list.value().entries : [],
  );
  protected readonly open = computed(() => this.entries().filter((entry) => !entry.checked));
  protected readonly basket = computed(() => this.entries().filter((entry) => entry.checked));
  protected readonly empty = computed(() => this.entries().length === 0);

  /** The catalog product with exactly the typed name, if there is one. */
  private readonly matchingProduct = computed(() => {
    const name = this.food().trim().toLowerCase();
    return name
      ? this.productList().find((product) => product.name.toLowerCase() === name)
      : undefined;
  });
  protected readonly suggestions = computed(() => {
    const text = this.food().trim().toLowerCase();
    if (!text) {
      return [];
    }
    return this.productList()
      .filter((product) => product.name.toLowerCase().includes(text))
      .slice(0, 8);
  });

  /** Commands in flight. Their answers may arrive out of order, so overlapping ones end with a reload. */
  private pending = 0;
  private overlapped = false;

  protected productEmoji(product: Product): string {
    return this.emojis().get(product.id) ?? '';
  }

  /** The category emoji of a product entry; nothing for free text. */
  protected emojiOf(entry: ShoppingEntry): string {
    return entry.productId ? (this.emojis().get(entry.productId) ?? '') : '';
  }

  protected add(event: Event): void {
    event.preventDefault();
    const name = this.food().trim();
    if (!name || this.adding()) {
      return;
    }
    const typed = { food: this.food(), quantity: this.quantity() };
    const product = this.matchingProduct();
    const quantity = typed.quantity.trim() || undefined;
    const request: AddEntryRequest = product
      ? { productId: product.id, quantity }
      : { name, quantity };
    this.adding.set(true);
    this.run(
      this.api.add(this.householdId(), request).pipe(finalize(() => this.adding.set(false))),
      () => {
        // Clear what was added, but keep what was typed in the meantime.
        if (this.food() === typed.food) {
          this.food.set('');
        }
        if (this.quantity() === typed.quantity) {
          this.quantity.set('');
        }
      },
    );
  }

  /** Ticks an entry off or puts it back; the list changes right away, the server's answer follows. */
  protected toggle(entry: ShoppingEntry): void {
    this.list.update((list) =>
      list
        ? {
            entries: list.entries.map((other) =>
              other.id === entry.id ? { ...other, checked: !entry.checked } : other,
            ),
          }
        : list,
    );
    const householdId = this.householdId();
    this.run(
      entry.checked
        ? this.api.uncheck(householdId, entry.id)
        : this.api.check(householdId, entry.id),
    );
  }

  protected remove(entry: ShoppingEntry): void {
    this.list.update((list) =>
      list ? { entries: list.entries.filter((other) => other.id !== entry.id) } : list,
    );
    this.run(this.api.remove(this.householdId(), entry.id));
  }

  protected clearBasket(): void {
    this.run(this.api.clearChecked(this.householdId()));
  }

  protected tickLabel(entry: ShoppingEntry): string {
    return entry.checked
      ? $localize`:@@shopping.untick:Put ${entry.name}:name: back on the list`
      : $localize`:@@shopping.tick:Tick off ${entry.name}:name:`;
  }

  protected removeLabel(entry: ShoppingEntry): string {
    return $localize`:@@common.removeNamed:Remove ${entry.name}:name:`;
  }

  private run(command: Observable<ShoppingList>, onSuccess?: () => void): void {
    this.pending++;
    this.overlapped ||= this.pending > 1;
    command.subscribe({
      next: (list) => {
        onSuccess?.();
        this.settle(list);
      },
      error: (error) => {
        this.settle(null);
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

  /** Shows the server's list once no other command is in flight. */
  private settle(list: ShoppingList | null): void {
    this.pending--;
    if (this.pending > 0) {
      return;
    }
    if (list && !this.overlapped) {
      this.list.set(list);
    } else {
      this.list.reload();
    }
    this.overlapped = false;
  }
}
