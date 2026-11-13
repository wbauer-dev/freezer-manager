package app.freezer.catalog;

import org.jmolecules.event.types.DomainEvent;

import app.freezer.household.HouseholdId;

/**
 * A member renamed a product in the household's catalog. Items still in the freezer follow; history keeps the old name.
 */
public record ProductRenamed(HouseholdId householdId, ProductId productId, String name) implements DomainEvent {

}
