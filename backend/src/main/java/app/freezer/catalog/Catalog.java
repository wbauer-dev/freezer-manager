package app.freezer.catalog;

import java.util.Optional;

import app.freezer.household.HouseholdId;

/**
 * Open host service of the Catalog module.
 */
public interface Catalog {

	/** The product, if it exists in the given household's catalog. */
	Optional<ProductDescription> findProduct(HouseholdId householdId, ProductId productId);

}
