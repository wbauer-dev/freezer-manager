package app.freezer.catalog.domain;

import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.household.HouseholdId;

/**
 * The catalogs of all households, one per household. Implemented by the persistence adapter.
 */
public interface HouseholdCatalogs extends Repository<HouseholdCatalog, HouseholdId> {

	HouseholdCatalog save(HouseholdCatalog catalog);

	Optional<HouseholdCatalog> findById(HouseholdId householdId);

	boolean existsById(HouseholdId householdId);

}
