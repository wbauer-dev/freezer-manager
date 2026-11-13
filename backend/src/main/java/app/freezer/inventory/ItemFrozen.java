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
 */
public record ItemFrozen(FrozenItemId itemId, HouseholdId householdId, ProductId productId, String productName,
		int portions, LocalDate frozenOn, LocalDate bestBefore, LabelCode labelCode) implements DomainEvent {

}
