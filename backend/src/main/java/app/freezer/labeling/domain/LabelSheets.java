package app.freezer.labeling.domain;

import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;

/**
 * The sticker sheets of all households, one per household. Implemented by the persistence adapter.
 */
public interface LabelSheets extends Repository<LabelSheet, HouseholdId> {

	LabelSheet save(LabelSheet sheet);

	Optional<LabelSheet> findById(HouseholdId householdId);

}
