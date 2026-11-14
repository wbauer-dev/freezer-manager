import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';

import { emojiByProduct } from '../catalog/catalog-lookup';
import { FoodCategory, Product } from '../catalog/catalog.model';
import { DATE_FORMAT, freshness, timeLeft } from '../shared/dates';
import { PhotoLoader } from '../media/photo-loader';
import { FoodAvatar } from '../shared/food-avatar';
import { LoadError, reloadFailed } from '../shared/load-error';
import { EatFirstEntry, ExpiryStatus, Freezer } from './inventory.model';

type Filter = 'ALL' | 'EXPIRING_SOON' | 'EXPIRED';

const GROUPS: { status: ExpiryStatus; title: string; icon: string }[] = [
  { status: 'EXPIRED', title: $localize`:@@eatFirst.group.now:Eat now`, icon: 'priority_high' },
  {
    status: 'EXPIRING_SOON',
    title: $localize`:@@eatFirst.group.soon:Eat soon`,
    icon: 'hourglass_top',
  },
  { status: 'FRESH', title: $localize`:@@eatFirst.group.fresh:Fresh`, icon: 'ac_unit' },
];

/**
 * Everything in the household's freezers, the items to eat first at the top. Combines Inventory's eat-first list
 * with the household's catalog (for the emoji of the food's category).
 */
@Component({
  selector: 'app-eat-first-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatRippleModule,
    FoodAvatar,
    LoadError,
  ],
  templateUrl: './eat-first-page.html',
  styleUrl: './eat-first-page.scss',
})
export class EatFirstPage {
  readonly householdId = input.required<string>();

  private readonly photos = inject(PhotoLoader);

  protected readonly entries = httpResource<EatFirstEntry[]>(
    () => `/api/households/${this.householdId()}/eat-first`,
  );
  protected readonly freezers = httpResource<Freezer[]>(
    () => `/api/households/${this.householdId()}/freezers`,
  );
  private readonly products = httpResource<Product[]>(
    () => `/api/households/${this.householdId()}/products`,
  );
  private readonly categories = httpResource<FoodCategory[]>(
    () => `/api/households/${this.householdId()}/food-categories`,
  );

  protected readonly dateFormat = DATE_FORMAT;
  protected readonly search = signal('');
  protected readonly filter = signal<Filter>('ALL');

  protected readonly emojiOf = computed(() =>
    emojiByProduct(
      this.products.hasValue() ? this.products.value() : [],
      this.categories.hasValue() ? this.categories.value() : [],
    ),
  );

  private readonly all = computed(() => (this.entries.hasValue() ? this.entries.value() : []));

  protected readonly stats = computed(() => {
    const entries = this.all();
    return {
      items: entries.length,
      portions: entries.reduce((sum, entry) => sum + entry.remainingPortions, 0),
      soon: entries.filter((entry) => entry.expiryStatus === 'EXPIRING_SOON').length,
      expired: entries.filter((entry) => entry.expiryStatus === 'EXPIRED').length,
    };
  });

  protected readonly visible = computed(() => {
    const text = this.search().trim().toLowerCase();
    const filter = this.filter();
    return this.all().filter(
      (entry) =>
        (filter === 'ALL' || entry.expiryStatus === filter) &&
        (text === '' ||
          entry.productName.toLowerCase().includes(text) ||
          (entry.note ?? '').toLowerCase().includes(text) ||
          entry.freezerName.toLowerCase().includes(text)),
    );
  });

  protected readonly groups = computed(() =>
    GROUPS.map((group) => ({
      ...group,
      entries: this.visible().filter((entry) => entry.expiryStatus === group.status),
    })).filter((group) => group.entries.length > 0),
  );

  protected reload(): void {
    reloadFailed(this.entries, this.freezers);
  }

  protected toggleFilter(filter: Filter): void {
    this.filter.update((current) => (current === filter ? 'ALL' : filter));
  }

  protected photo(entry: EatFirstEntry): string | null {
    return entry.photoId ? this.photos.url(this.householdId(), entry.photoId)() : null;
  }

  protected freshness(entry: EatFirstEntry): number {
    return freshness(entry.frozenOn, entry.bestBefore);
  }

  protected timeLeft(entry: EatFirstEntry): string {
    return timeLeft(entry.bestBefore, entry.expiryStatus);
  }

  protected statusClass(status: ExpiryStatus): string {
    return 'status-' + status.toLowerCase();
  }
}
