package app.freezer.inventory;

import org.jmolecules.event.types.DomainEvent;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * Portions were taken out of a frozen item.
 *
 * @param labelCode {@code null} for items frozen before labels existed
 * @param revision the item's revision with this event, see {@link ItemFrozen#revision()}
 */
public record PortionsTakenOut(FrozenItemId itemId, HouseholdId householdId, ProductId productId, int portions,
		int remaining, LabelCode labelCode, long revision) implements DomainEvent {

}
