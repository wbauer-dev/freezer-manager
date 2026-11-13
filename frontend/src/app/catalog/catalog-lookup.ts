import { FoodCategory, Product } from './catalog.model';

/** The emoji of each product's category, for showing food from other contexts (e.g. items in the freezer). */
export function emojiByProduct(
  products: Product[],
  categories: FoodCategory[],
): Map<string, string> {
  const emojiOf = new Map(categories.map((category) => [category.id, category.emoji]));
  return new Map(products.map((product) => [product.id, emojiOf.get(product.categoryId) ?? '']));
}
