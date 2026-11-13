package app.freezer.inventory.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import app.freezer.household.HouseholdId;
import app.freezer.inventory.domain.CompartmentId;

/**
 * Read models that span several aggregates. Implemented with plain SQL in the persistence adapter; they never change
 * data (CQRS light).
 */
public interface InventoryQueries {

	/** All items in the household's freezers, the ones to eat first at the top. */
	List<EatFirstEntry> eatFirst(HouseholdId householdId, LocalDate today);

	/** Number of items in the freezer per compartment. Empty compartments are missing. */
	Map<CompartmentId, Integer> itemCountsByCompartment(HouseholdId householdId);

}
