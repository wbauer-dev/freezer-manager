package app.freezer.inventory;

import org.jmolecules.event.types.DomainEvent;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * An item was removed from the freezer without being eaten.
 *
 * @param labelCode {@code null} for items frozen before labels existed
 */
public record ItemDiscarded(FrozenItemId itemId, HouseholdId householdId, ProductId productId, DiscardReason reason,
		LabelCode labelCode) implements DomainEvent {

}
