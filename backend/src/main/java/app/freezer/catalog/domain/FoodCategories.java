package app.freezer.catalog.domain;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;

/**
 * The food categories of all households. Implemented by the persistence adapter.
 */
public interface FoodCategories extends Repository<FoodCategory, FoodCategoryId> {

	FoodCategory save(FoodCategory category);

	Optional<FoodCategory> findById(FoodCategoryId id);

	/** The household's categories in display order. */
	List<FoodCategory> findAllOfHousehold(HouseholdId householdId);

	/** The household's category with this name (ignoring case). */
	Optional<FoodCategory> findByName(HouseholdId householdId, String name);

	int nextPosition(HouseholdId householdId);

	void delete(FoodCategory category);

	void deleteAllOfHousehold(HouseholdId householdId);

}
