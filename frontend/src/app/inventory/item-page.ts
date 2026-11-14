import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarRef } from '@angular/material/snack-bar';
import { Observable, firstValueFrom } from 'rxjs';

import { emojiByProduct } from '../catalog/catalog-lookup';
import { FoodCategory, Product } from '../catalog/catalog.model';
import { LabelingApi } from '../labeling/labeling-api';
import { ScannedLabel, StickerLayout } from '../labeling/labeling.model';
import { MediaApi } from '../media/media-api';
import { PhotoLoader } from '../media/photo-loader';
import { preparePhoto } from '../media/prepare-photo';
import { formatLabelCode } from '../shared/label-code';
import { DATE_FORMAT, freshness, timeLeft } from '../shared/dates';
import { UNKNOWN_FOOD } from '../shared/food';
import { FoodAvatar } from '../shared/food-avatar';
import { problemMessage } from '../shared/problem-message';
import { InventoryApi } from './inventory-api';
import { DISCARD_REASONS, DiscardReason, FrozenItem } from './inventory.model';

/** Up to this many portions are shown as tappable tiles; above, a stepper is used. */
const MAX_TILES = 24;

@Component({
  selector: 'app-item-page',
  imports: [
    CdkTrapFocus,
    DatePipe,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    FoodAvatar,
  ],
  templateUrl: './item-page.html',
  styleUrl: './item-page.scss',
  host: { '(document:keydown.escape)': 'closePhoto()' },
})
export class ItemPage {
  readonly householdId = input.required<string>();
  readonly itemId = input.required<string>();

  private readonly api = inject(InventoryApi);
  private readonly labelingApi = inject(LabelingApi);
  private readonly mediaApi = inject(MediaApi);
  private readonly photos = inject(PhotoLoader);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly formatLabelCode = formatLabelCode;

  protected readonly item = httpResource<FrozenItem>(
    () => `/api/households/${this.householdId()}/items/${this.itemId()}`,
  );
  private readonly products = httpResource<Product[]>(
    () => `/api/households/${this.householdId()}/products`,
  );
  private readonly categories = httpResource<FoodCategory[]>(
    () => `/api/households/${this.householdId()}/food-categories`,
  );
  /** The label behind the item's code: one-time or persistent decides which stickers make sense. */
  protected readonly label = httpResource<ScannedLabel>(() => {
    const code = this.item.hasValue() ? this.item.value().labelCode : null;
    return code ? `/api/labels/${code}` : undefined;
  });

  protected readonly portionsToTakeOut = signal(1);
  protected readonly busy = signal(false);
  /** Offers to undo the last take-out; it goes when the take-out is undone on the page, or the page is left. */
  private undoSnackBar: MatSnackBarRef<unknown> | null = null;
  protected readonly celebrate = signal(false);
  protected readonly discardReasons = DISCARD_REASONS;
  protected readonly dateFormat = DATE_FORMAT;
  protected readonly snowflakes = Array.from({ length: 22 }, (_, i) => ({
    x: (i * 37) % 100,
    delay: ((i * 7) % 10) / 10,
    size: 12 + ((i * 5) % 14),
  }));

  private readonly current = computed(() => (this.item.hasValue() ? this.item.value() : null));

  protected readonly emoji = computed(() => {
    const item = this.current();
    const emojis = emojiByProduct(
      this.products.hasValue() ? this.products.value() : [],
      this.categories.hasValue() ? this.categories.value() : [],
    );
    return (item && emojis.get(item.productId)) || UNKNOWN_FOOD;
  });

  protected readonly photoUrl = computed(() => {
    const item = this.current();
    return item?.photoId ? this.photos.url(this.householdId(), item.photoId)() : null;
  });
  protected readonly showingPhoto = signal(false);
  private readonly zoomButton = viewChild<ElementRef<HTMLButtonElement>>('zoomButton');
  private readonly lightboxClose = viewChild('lightboxClose', {
    read: ElementRef<HTMLButtonElement>,
  });
  private readonly injector = inject(Injector);
  protected readonly largePhotoUrl = computed(() => {
    const item = this.current();
    return this.showingPhoto() && item?.photoId
      ? this.photos.url(this.householdId(), item.photoId, 'LARGE')()
      : null;
  });
  protected readonly uploadingPhoto = signal(false);

  protected readonly freshness = computed(() => {
    const item = this.current();
    return item ? freshness(item.frozenOn, item.bestBefore) : 0;
  });
  protected readonly timeLeft = computed(() => {
    const item = this.current();
    return item ? timeLeft(item.bestBefore, item.expiryStatus) : '';
  });

  protected readonly statusLabel = computed(() => {
    const item = this.current();
    if (!item) {
      return '';
    }
    if (item.state === 'CONSUMED') {
      return $localize`:@@item.status.eaten:All eaten`;
    }
    if (item.state === 'DISCARDED') {
      return $localize`:@@item.status.discarded:Discarded`;
    }
    return {
      FRESH: $localize`:@@item.status.fresh:Fresh`,
      EXPIRING_SOON: $localize`:@@item.status.soon:Eat soon`,
      EXPIRED: $localize`:@@item.status.expired:Expired`,
    }[item.expiryStatus];
  });

  protected readonly statusClass = computed(() => {
    const item = this.current();
    return item ? 'status-' + item.expiryStatus.toLowerCase() : '';
  });

  protected readonly tiles = computed(() => {
    const item = this.current();
    return item && item.initialPortions <= MAX_TILES
      ? Array.from({ length: item.initialPortions }, (_, i) => i)
      : null;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.undoSnackBar?.dismiss());
  }

  /** Shows the photo large. Focus moves into it, and back to the photo when it is closed. */
  protected openPhoto(): void {
    this.showingPhoto.set(true);
    afterNextRender(() => this.lightboxClose()?.nativeElement.focus(), {
      injector: this.injector,
    });
  }

  protected closePhoto(): void {
    if (this.showingPhoto()) {
      this.showingPhoto.set(false);
      this.zoomButton()?.nativeElement.focus();
    }
  }

  /** Tapping a tile selects it and every remaining tile to its right. */
  protected selectFrom(index: number): void {
    this.portionsToTakeOut.set(this.remaining() - index);
  }

  protected isSelected(index: number): boolean {
    return index < this.remaining() && index >= this.remaining() - this.portionsToTakeOut();
  }

  protected changePortions(delta: number): void {
    this.portionsToTakeOut.update((count) =>
      Math.min(Math.max(count + delta, 1), this.remaining()),
    );
  }

  protected remaining(): number {
    return this.current()?.remainingPortions ?? 0;
  }

  protected takeOut(): void {
    const portions = this.portionsToTakeOut();
    this.run(this.api.takeOut(this.householdId(), this.itemId(), portions), (item) => {
      this.portionsToTakeOut.set(1);
      if (item.state === 'CONSUMED') {
        this.celebrate.set(true);
        setTimeout(() => this.celebrate.set(false), 3200);
      }
      const message =
        item.state === 'CONSUMED'
          ? $localize`:@@item.takenOut.last:All taken out — that was the last 🎉`
          : portions === 1
            ? $localize`:@@item.takenOut.one:1 portion taken out`
            : $localize`:@@item.takenOut.many:${portions}:count: portions taken out`;
      this.undoSnackBar = this.snackBar.open(message, $localize`:@@common.undo:Undo`, {
        duration: 10000,
      });
      this.undoSnackBar.onAction().subscribe(() => this.undoTakeOut());
    });
  }

  protected undoTakeOut(): void {
    // Undo once: the snackbar's Undo would take back the withdrawal before this one.
    this.undoSnackBar?.dismiss();
    this.run(this.api.undoTakeOut(this.householdId(), this.itemId()), () => {
      this.celebrate.set(false);
      this.snackBar.open(
        $localize`:@@item.undone:Take-out undone — back in the freezer ❄️`,
        undefined,
        {
          duration: 3000,
        },
      );
    });
  }

  protected async changePhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.uploadingPhoto.set(true);
    try {
      const prepared = await preparePhoto(file);
      const uploaded = await firstValueFrom(this.mediaApi.upload(this.householdId(), prepared));
      this.photos.remember(uploaded.id, prepared);
      this.item.set(
        await firstValueFrom(this.api.attachPhoto(this.householdId(), this.itemId(), uploaded.id)),
      );
    } catch (error) {
      this.snackBar.open(
        problemMessage(
          error,
          $localize`:@@freeze.photo.uploadError:The photo could not be uploaded.`,
        ),
        $localize`:@@common.ok:OK`,
        { duration: 6000 },
      );
    } finally {
      this.uploadingPhoto.set(false);
    }
  }

  /** Queues a sticker for the item's label, e.g. because the old one came off. */
  protected printSticker(code: string, layout: StickerLayout): void {
    this.labelingApi.enqueue(this.householdId(), code, layout, this.itemId()).subscribe({
      next: () =>
        this.snackBar
          .open(
            $localize`:@@item.sticker.queued:Sticker added to the print queue`,
            $localize`:@@common.open:Open`,
            {
              duration: 6000,
            },
          )
          .onAction()
          .subscribe(() => this.router.navigate(['/households', this.householdId(), 'labels'])),
      error: (error) =>
        this.snackBar.open(
          problemMessage(error, $localize`:@@item.sticker.error:The label could not be queued.`),
          $localize`:@@common.ok:OK`,
          { duration: 6000 },
        ),
    });
  }

  protected discard(reason: DiscardReason): void {
    this.run(this.api.discard(this.householdId(), this.itemId(), reason));
  }

  protected reasonLabel(reason: DiscardReason | null): string {
    return DISCARD_REASONS.find((r) => r.value === reason)?.label ?? '';
  }

  protected photoButtonLabel(hasPhoto: boolean): string {
    return hasPhoto
      ? $localize`:@@item.photo.change:Change photo`
      : $localize`:@@freeze.photo.add:Add a photo`;
  }

  protected tileLabel(index: number, remaining: number): string {
    return index >= remaining
      ? $localize`:@@item.tile.taken:Already taken out`
      : $localize`:@@item.tile.takeOut:Take out ${remaining - index}:count:`;
  }

  protected photoAlt(name: string): string {
    return $localize`:@@item.photo.alt:Photo of ${name}:name:`;
  }

  protected notFound(): boolean {
    const error = this.item.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  }

  private run(command: Observable<FrozenItem>, onSuccess?: (item: FrozenItem) => void): void {
    this.busy.set(true);
    command.subscribe({
      next: (item) => {
        this.item.set(item);
        this.busy.set(false);
        onSuccess?.(item);
      },
      error: (error) => {
        this.busy.set(false);
        this.item.reload();
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
