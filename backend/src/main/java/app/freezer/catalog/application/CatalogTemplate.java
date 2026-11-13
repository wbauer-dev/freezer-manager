package app.freezer.catalog.application;

import java.util.List;

/**
 * System defaults for a household's catalog in one language: categories with typical products.
 */
public record CatalogTemplate(String language, String name, List<Category> categories) {

	/** A default food category with its typical products. The key identifies it across languages. */
	public record Category(String key, String emoji, int shelfLifeMonths, String name, List<String> products) {
	}

}
