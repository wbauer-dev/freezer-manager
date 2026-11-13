package app.freezer.household.domain;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;

/**
 * All households. Implemented by the persistence adapter.
 */
public interface Households extends Repository<Household, HouseholdId> {

	Household save(Household household);

	Optional<Household> findById(HouseholdId id);

	/** Households the given user is a member of, ordered by name. */
	List<Household> findAllWithMember(UserId userId);

}
