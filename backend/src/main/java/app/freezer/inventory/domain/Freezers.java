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

	/**
	 * Like {@link #findById}, and makes other transactions that lock the same freezer wait until this one ends.
	 * <p>
	 * Putting an item into a compartment and removing that compartment (or the whole freezer) check each other: the
	 * item needs the compartment, the removal needs it to be empty. Neither sees what the other has not committed yet,
	 * so both could succeed at the same moment. That is why use cases that put an item in, or remove a part of a
	 * freezer, load it with this lock first: they run one after the other, and the second sees what the first did.
	 */
	Optional<Freezer> findAndLock(FreezerId id);

	/** The household's freezers that have not been removed, ordered by name. */
	List<Freezer> findAllOfHousehold(HouseholdId householdId);

	/** Whether the household already uses this name for a freezer (ignoring case and removed freezers). */
	boolean existsWithName(HouseholdId householdId, String name);

}
