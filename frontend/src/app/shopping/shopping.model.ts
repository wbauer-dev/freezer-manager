/** Representations returned by the backend's Shopping module. */

/** Why an entry is on the list. Staples running low and recipes will add their own. */
export type EntrySource = 'MANUAL';

export interface ShoppingEntry {
  id: string;
  /** The catalog product, or null for free text. */
  productId: string | null;
  name: string;
  quantity: string | null;
  source: EntrySource;
  addedBy: string;
  addedAt: string;
  checked: boolean;
  checkedBy: string | null;
  checkedAt: string | null;
}

export interface ShoppingList {
  /** In the order they were added, open and ticked-off ones alike. */
  entries: ShoppingEntry[];
}

/** A catalog product or free text, never both. */
export interface AddEntryRequest {
  productId?: string;
  name?: string;
  quantity?: string;
}

export const MAX_NAME_LENGTH = 60;
export const MAX_QUANTITY_LENGTH = 60;
