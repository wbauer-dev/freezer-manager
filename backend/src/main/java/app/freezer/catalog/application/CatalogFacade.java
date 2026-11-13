package app.freezer.catalog.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.catalog.Catalog;
import app.freezer.catalog.ProductDescription;
import app.freezer.catalog.ProductId;
import app.freezer.catalog.domain.FoodCategories;
import app.freezer.catalog.domain.Products;
import app.freezer.household.HouseholdId;

/**
 * Implements the module's published API. Other modules only see {@link ProductDescription}, never the aggregates.
 */
@Service
@Transactional(readOnly = true)
class CatalogFacade implements Catalog {

	private final Products products;
	private final FoodCategories categories;

	CatalogFacade(Products products, FoodCategories categories) {
		this.products = products;
		this.categories = categories;
	}

	@Override
	public Optional<ProductDescription> findProduct(HouseholdId householdId, ProductId productId) {
		return products.findById(productId)
				.filter(product -> product.belongsTo(householdId))
				.flatMap(product -> categories.findById(product.getCategoryId())
						.map(category -> new ProductDescription(product.getId(), product.getName(),
								category.shelfLife())));
	}

}
