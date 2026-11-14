import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarRef } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';

import { emojiByProduct } from '../catalog/catalog-lookup';
import { FoodCategory, Product } from '../catalog/catalog.model';
import { HouseholdDetails } from '../household/household.model';
import { InventoryApi } from '../inventory/inventory-api';
import { FrozenItem } from '../inventory/inventory.model';
import { PhotoLoader } from '../media/photo-loader';
import { DATE_FORMAT, freshness, timeLeft } from '../shared/dates';
import { FoodAvatar } from '../shared/food-avatar';
import { formatLabelCode, labelCodeFromQr } from '../shared/label-code';
import { problemMessage } from '../shared/problem-message';
import { LabelingApi } from './labeling-api';
import { ScannedLabel } from './labeling.model';
import { CameraError, CameraProblem, QrScanner } from './qr-scanner';

type ScanResult =
  | { kind: 'item'; label: ScannedLabel; item: FrozenItem; emoji: string; householdName: string }
  | { kind: 'free'; label: ScannedLabel }
  | { kind: 'used-up'; label: ScannedLabel }
  | { kind: 'unknown'; code: string }
  | { kind: 'not-a-label' }
  | { kind: 'error' };

/**
 * Scans a label with the camera inside the app and shows what is in the package, ready to take portions out. Labels
 * of all the user's households can be scanned. Combines Labeling (which item carries the label), Inventory (the item
 * and taking out) and Catalog (the food's emoji).
 */
@Component({
  selector: 'app-scanner-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, FoodAvatar],
  templateUrl: './scanner-page.html',
  styleUrl: './scanner-page.scss',
})
export class ScannerPage {
  private readonly http = inject(HttpClient);
  private readonly labeling = inject(LabelingApi);
  private readonly inventory = inject(InventoryApi);
  private readonly photos = inject(PhotoLoader);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly scanner = new QrScanner();
  private cameraAttempt = 0;
  /** Offers to undo the last take-out; it belongs to the item shown and goes when the next label is scanned. */
  private undoSnackBar: MatSnackBarRef<unknown> | null = null;

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');

  protected readonly phase = signal<'camera' | 'resolving' | 'result'>('camera');
  protected readonly cameraProblem = signal<CameraProblem | null>(null);
  protected readonly cameraStarting = signal(true);
  protected readonly result = signal<ScanResult | null>(null);
  protected readonly portions = signal(1);
  protected readonly busy = signal(false);

  protected readonly dateFormat = DATE_FORMAT;
  protected readonly format = formatLabelCode;

  /** The scanned item, while it is shown. */
  protected readonly item = computed(() => {
    const result = this.result();
    return result?.kind === 'item' ? result : null;
  });
  protected readonly freeLabel = computed(() => {
    const result = this.result();
    return result?.kind === 'free' ? result.label : null;
  });
  protected readonly usedUpLabel = computed(() => {
    const result = this.result();
    return result?.kind === 'used-up' ? result.label : null;
  });
  protected readonly unknownCode = computed(() => {
    const result = this.result();
    return result?.kind === 'unknown' ? result.code : null;
  });
  protected readonly photoUrl = computed(() => {
    const scanned = this.item();
    return scanned?.item.photoId
      ? this.photos.url(scanned.label.householdId, scanned.item.photoId)()
      : null;
  });
  protected readonly freshness = computed(() => {
    const scanned = this.item();
    return scanned ? freshness(scanned.item.frozenOn, scanned.item.bestBefore) : null;
  });
  protected readonly timeLeft = computed(() => {
    const scanned = this.item();
    return scanned ? timeLeft(scanned.item.bestBefore) : '';
  });

  constructor() {
    afterNextRender(() => this.startCamera());
    inject(DestroyRef).onDestroy(() => {
      this.scanner.stop();
      this.undoSnackBar?.dismiss();
    });
  }

  protected scanNext(): void {
    this.undoSnackBar?.dismiss();
    this.result.set(null);
    this.phase.set('camera');
    this.startCamera();
  }

  /** For when there is no camera, or a sticker is too damaged to scan: the code is printed below the QR code. */
  protected enterCode(input: HTMLInputElement): void {
    const text = input.value.trim();
    if (text) {
      input.value = '';
      this.handle(text);
    }
  }

  protected changePortions(delta: number): void {
    const remaining = this.item()?.item.remainingPortions ?? 1;
    this.portions.update((count) => Math.min(Math.max(count + delta, 1), remaining));
  }

  protected takeOut(): void {
    const scanned = this.item();
    if (!scanned) {
      return;
    }
    const portions = this.portions();
    const householdId = scanned.label.householdId;
    this.busy.set(true);
    this.inventory.takeOut(householdId, scanned.item.id, portions).subscribe({
      next: (item) => {
        this.busy.set(false);
        this.showItem(item);
        this.portions.set(1);
        const message =
          item.state === 'CONSUMED'
            ? $localize`:@@item.takenOut.last:All taken out — that was the last 🎉`
            : portions === 1
              ? $localize`:@@item.takenOut.one:1 portion taken out`
              : $localize`:@@item.takenOut.many:${portions}:count: portions taken out`;
        this.undoSnackBar = this.snackBar.open(message, $localize`:@@common.undo:Undo`, {
          duration: 10000,
        });
        this.undoSnackBar.onAction().subscribe(() => this.undo(householdId, item.id));
      },
      error: (error) => this.failed(error),
    });
  }

  protected close(): void {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      this.router.navigate(['/households']);
    }
  }

  private undo(householdId: string, itemId: string): void {
    this.inventory.undoTakeOut(householdId, itemId).subscribe({
      next: (item) => this.showItem(item),
      error: (error) => this.failed(error),
    });
  }

  /** Shows the item's new state — unless another label was scanned in the meantime. */
  private showItem(item: FrozenItem): void {
    this.result.update((result) =>
      result?.kind === 'item' && result.item.id === item.id ? { ...result, item } : result,
    );
  }

  private async startCamera(): Promise<void> {
    const attempt = ++this.cameraAttempt;
    this.cameraProblem.set(null);
    this.cameraStarting.set(true);
    try {
      await this.scanner.start(this.video().nativeElement, (text) => this.handle(text));
    } catch (error) {
      this.cameraProblem.set(error instanceof CameraError ? error.problem : 'unavailable');
    } finally {
      // An earlier attempt that was overtaken must not end the waiting animation of the current one.
      if (attempt === this.cameraAttempt) {
        this.cameraStarting.set(false);
      }
    }
  }

  private handle(text: string): void {
    this.scanner.stop();
    navigator.vibrate?.(40);
    const code = labelCodeFromQr(text);
    if (!code) {
      this.show({ kind: 'not-a-label' });
      return;
    }
    this.phase.set('resolving');
    this.labeling.scan(code).subscribe({
      next: (label) => {
        if (label.state === 'ATTACHED' && label.itemId) {
          this.loadItem(label, label.itemId);
        } else if (label.state === 'FREE') {
          this.show({ kind: 'free', label });
        } else {
          this.show({ kind: 'used-up', label });
        }
      },
      error: (error) =>
        this.show(
          error instanceof HttpErrorResponse && error.status === 404
            ? { kind: 'unknown', code }
            : { kind: 'error' },
        ),
    });
  }

  private loadItem(label: ScannedLabel, itemId: string): void {
    const household = `/api/households/${label.householdId}`;
    forkJoin({
      item: this.inventory.item(label.householdId, itemId),
      products: this.http.get<Product[]>(`${household}/products`),
      categories: this.http.get<FoodCategory[]>(`${household}/food-categories`),
      household: this.http.get<HouseholdDetails>(household),
    }).subscribe({
      next: ({ item, products, categories, household }) => {
        this.portions.set(1);
        this.show({
          kind: 'item',
          label,
          item,
          emoji: emojiByProduct(products, categories).get(item.productId) ?? '',
          householdName: household.name,
        });
      },
      error: () => this.show({ kind: 'error' }),
    });
  }

  private show(result: ScanResult): void {
    this.result.set(result);
    this.phase.set('result');
  }

  private failed(error: unknown): void {
    this.busy.set(false);
    this.snackBar.open(
      problemMessage(error, $localize`:@@common.error.retry:That did not work. Please try again.`),
      $localize`:@@common.ok:OK`,
      { duration: 6000 },
    );
  }
}
