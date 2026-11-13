package app.freezer.inventory;

import java.util.Optional;

import app.freezer.household.HouseholdId;

/**
 * Published query API: what a frozen item is, for modules that show or print it (e.g. Labeling for stickers).
 */
public interface FrozenItemDirectory {

	/** The item, if it exists in the household. */
	Optional<ItemDescription> describe(HouseholdId householdId, FrozenItemId itemId);

}
