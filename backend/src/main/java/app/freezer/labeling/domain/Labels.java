package app.freezer.labeling.domain;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * All labels of all households, by their code. Implemented by the persistence adapter.
 */
public interface Labels extends Repository<Label, LabelCode> {

	Label save(Label label);

	Optional<Label> findById(LabelCode code);

	/** How many of the household's persistent labels are in the given state. */
	int countPersistent(HouseholdId householdId, LabelState state);

	/** The household's persistent labels that are on no item right now, ordered by code. */
	List<Label> findFreePersistent(HouseholdId householdId);

}
