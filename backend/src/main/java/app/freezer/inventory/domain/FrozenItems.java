package app.freezer.inventory.domain;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.sharedkernel.LabelCode;

/**
 * All frozen items, including eaten and discarded ones. Implemented by the persistence adapter.
 */
public interface FrozenItems extends Repository<FrozenItem, FrozenItemId> {

	FrozenItem save(FrozenItem item);

	Optional<FrozenItem> findById(FrozenItemId id);

	/** The items of this product that are currently in a freezer. */
	List<FrozenItem> findInFreezerByProduct(ProductId productId);

	/** Whether any item is currently stored in the compartment. */
	boolean anyInCompartment(CompartmentId compartmentId);

	/** Whether any item is currently stored in the freezer. */
	boolean anyInFreezer(FreezerId freezerId);

	/** Whether an item in the household's freezers carries this label. */
	boolean labelInUse(HouseholdId householdId, LabelCode labelCode);

}
