/**
 * <b>Freezer Inventory — core domain.</b>
 * <p>
 * Freezers and compartments, frozen items with portions, best-before dates, stock per product and
 * the eat-first list. Publishes {@code ItemFrozen}, {@code ItemConsumed}, {@code ItemDiscarded} and
 * {@code StockLevelChanged}. See {@code docs/domain-model.md}, section 5.1.
 */
@ApplicationModule(displayName = "Freezer Inventory")
package app.freezer.inventory;

import org.springframework.modulith.ApplicationModule;
