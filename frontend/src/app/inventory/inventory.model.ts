/** Representations returned by the backend's Inventory module. */

export type ExpiryStatus = 'EXPIRED' | 'EXPIRING_SOON' | 'FRESH';
export type ItemState = 'IN_FREEZER' | 'CONSUMED' | 'DISCARDED';
export type DiscardReason = 'EXPIRED' | 'FREEZER_BURN' | 'OTHER';

export const DISCARD_REASONS: { value: DiscardReason; label: string }[] = [
  { value: 'EXPIRED', label: $localize`:@@discardReason.expired:Expired` },
  { value: 'FREEZER_BURN', label: $localize`:@@discardReason.freezerBurn:Freezer burn` },
  { value: 'OTHER', label: $localize`:@@discardReason.other:Other reason` },
];

export interface Freezer {
  id: string;
  name: string;
  compartments: Compartment[];
}

export interface Compartment {
  id: string;
  name: string;
  itemCount: number;
}

export interface EatFirstEntry {
  itemId: string;
  productId: string;
  photoId: string | null;
  productName: string;
  note: string | null;
  remainingPortions: number;
  gramsPerPortion: number | null;
  frozenOn: string;
  bestBefore: string;
  expiryStatus: ExpiryStatus;
  freezerId: string;
  freezerName: string;
  compartmentId: string;
  compartmentName: string;
}

export interface FrozenItem {
  id: string;
  productId: string;
  photoId: string | null;
  productName: string;
  note: string | null;
  labelCode: string | null;
  state: ItemState;
  initialPortions: number;
  remainingPortions: number;
  gramsPerPortion: number | null;
  frozenOn: string;
  bestBefore: string;
  bestBeforeOverridden: boolean;
  expiryStatus: ExpiryStatus;
  freezerId: string;
  freezerName: string;
  compartmentId: string;
  compartmentName: string | null;
  discardReason: DiscardReason | null;
  removedAt: string | null;
  canUndoTakeOut: boolean;
  withdrawals: Withdrawal[];
}

export interface Withdrawal {
  portions: number;
  takenBy: string;
  takenAt: string;
}

export interface FreezeRequest {
  productId: string;
  freezerId: string;
  compartmentId: string;
  portions: number;
  gramsPerPortion: number | null;
  frozenOn: string;
  bestBefore: string | null;
  note: string | null;
  /** A label the member already has; a new one is created if null. */
  labelCode: string | null;
  photoId: string | null;
}
