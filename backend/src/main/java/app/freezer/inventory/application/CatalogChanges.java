package app.freezer.inventory.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import app.freezer.catalog.ProductRenamed;
import app.freezer.inventory.domain.FrozenItems;

/**
 * Keeps the names of items in the freezer in line with the household's catalog.
 */
@Component
class CatalogChanges {

	private final FrozenItems items;

	CatalogChanges(FrozenItems items) {
		this.items = items;
	}

	@ApplicationModuleListener
	void on(ProductRenamed event) {
		for (var item : items.findInFreezerByProduct(event.productId())) {
			if (item.belongsTo(event.householdId())) {
				item.productRenamed(event.name());
				items.save(item);
			}
		}
	}

}
