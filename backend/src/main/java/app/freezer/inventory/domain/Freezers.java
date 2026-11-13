package app.freezer.inventory.domain;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;

/**
 * The freezers of all households, including removed ones. Implemented by the persistence adapter.
 */
public interface Freezers extends Repository<Freezer, FreezerId> {

	Freezer save(Freezer freezer);

	Optional<Freezer> findById(FreezerId id);

	/** The household's freezers that have not been removed, ordered by name. */
	List<Freezer> findAllOfHousehold(HouseholdId householdId);

	/** Whether the household already uses this name for a freezer (ignoring case and removed freezers). */
	boolean existsWithName(HouseholdId householdId, String name);

}
