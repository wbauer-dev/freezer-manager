/** Representations returned by the backend's Labeling module. */

export type LabelKind = 'ONE_TIME' | 'PERSISTENT';
export type LabelState = 'FREE' | 'ATTACHED' | 'USED_UP';
export type StickerLayout = 'QR_ONLY' | 'QR_AND_CONTENT' | 'CONTENT_ONLY';

export const LAYOUT_NAMES: Record<StickerLayout, string> = {
  QR_ONLY: $localize`:@@layout.qrOnly:QR only`,
  QR_AND_CONTENT: $localize`:@@layout.qrAndContent:QR + contents`,
  CONTENT_ONLY: $localize`:@@layout.contentOnly:Contents only`,
};

export interface ScannedLabel {
  code: string;
  householdId: string;
  kind: LabelKind;
  state: LabelState;
  itemId: string | null;
}

export interface LabelSheet {
  columns: number;
  rows: number;
  usedPositions: number[];
  nextPosition: number;
  queue: QueuedSticker[];
}

/** What will be printed: the label is only the identity behind the QR code. */
export interface QueuedSticker {
  code: string;
  layout: StickerLayout;
  title: string | null;
  frozenOn: string | null;
  bestBefore: string | null;
}

export interface LabelStock {
  freePersistent: number;
  persistentInUse: number;
}
