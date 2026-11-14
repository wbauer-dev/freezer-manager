package app.freezer.inventory;

import java.time.LocalDate;

import org.jmolecules.event.types.DomainEvent;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * A new item was put into a freezer.
 *
 * @param labelCode {@code null} for items frozen before labels existed
 * @param revision the item's revision with this event: {@code 1} here, one more with each further event of the item
 *        (see {@code FrozenItem}). {@code 0} in publications recorded before items had revisions: unknown.
 */
public record ItemFrozen(FrozenItemId itemId, HouseholdId householdId, ProductId productId, String productName,
		int portions, LocalDate frozenOn, LocalDate bestBefore, LabelCode labelCode, long revision)
		implements DomainEvent {

}
