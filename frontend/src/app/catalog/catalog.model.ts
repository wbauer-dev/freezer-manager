/** Representations returned by the backend's Catalog module. Each household has its own catalog. */

export interface FoodCategory {
  id: string;
  name: string;
  emoji: string;
  shelfLifeMonths: number;
}

export interface Product {
  id: string;
  name: string;
  categoryId: string;
}

export interface CatalogInfo {
  /** Language of the system defaults the catalog started with; null if it started empty. */
  startedWith: string | null;
  startedAt: string;
}

export interface CatalogLanguage {
  code: string;
  name: string;
}

export type ResetMode = 'EMPTY' | 'DEFAULTS';

export const MAX_NAME_LENGTH = 40;
export const MAX_SHELF_LIFE_MONTHS = 60;
