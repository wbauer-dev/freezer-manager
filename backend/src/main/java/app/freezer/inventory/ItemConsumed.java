package app.freezer.inventory;

import org.jmolecules.event.types.DomainEvent;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * The last portion of an item was taken out.
 *
 * @param labelCode {@code null} for items frozen before labels existed
 * @param revision the item's revision with this event, see {@link ItemFrozen#revision()}
 */
public record ItemConsumed(FrozenItemId itemId, HouseholdId householdId, ProductId productId, LabelCode labelCode,
		long revision) implements DomainEvent {

}
