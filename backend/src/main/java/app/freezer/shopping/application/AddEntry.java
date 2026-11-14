package app.freezer.shopping.application;

import app.freezer.catalog.ProductId;

/**
 * Something to put on the shopping list: a product of the household's catalog or free text, never both.
 *
 * @param productId the product, or null for free text
 * @param name the free text, or null for a product
 * @param quantity an optional note such as "500 g"
 */
public record AddEntry(ProductId productId, String name, String quantity) {

	public AddEntry {
		if (productId != null && name != null && !name.isBlank()) {
			throw new IllegalArgumentException("shopping.entry.product-or-name");
		}
	}

}
