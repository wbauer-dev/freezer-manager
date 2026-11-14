package app.freezer.shopping.domain;

import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;

/**
 * The shopping lists of all households, one per household. Implemented by the persistence adapter.
 */
public interface ShoppingLists extends Repository<ShoppingList, HouseholdId> {

	ShoppingList save(ShoppingList list);

	Optional<ShoppingList> findById(HouseholdId householdId);

}
