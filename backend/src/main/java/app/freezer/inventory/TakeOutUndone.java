package app.freezer.inventory;

import org.jmolecules.event.types.DomainEvent;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * A mistaken take-out was undone; the portions are back in the freezer.
 *
 * @param labelCode {@code null} for items frozen before labels existed
 * @param revision the item's revision with this event, see {@link ItemFrozen#revision()}
 */
public record TakeOutUndone(FrozenItemId itemId, HouseholdId householdId, ProductId productId, int portions,
		int remaining, LabelCode labelCode, long revision) implements DomainEvent {

}
