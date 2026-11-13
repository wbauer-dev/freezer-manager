package app.freezer.catalog.domain;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;

/**
 * The products of all households. Implemented by the persistence adapter.
 */
public interface Products extends Repository<Product, ProductId> {

	Product save(Product product);

	Optional<Product> findById(ProductId id);

	/** The household's products, ordered by name. */
	List<Product> findAllOfHousehold(HouseholdId householdId);

	/** The household's product with this name (ignoring case). */
	Optional<Product> findByName(HouseholdId householdId, String name);

	boolean anyInCategory(FoodCategoryId categoryId);

	void delete(Product product);

	void deleteAllOfHousehold(HouseholdId householdId);

}
