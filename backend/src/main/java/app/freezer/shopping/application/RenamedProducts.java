package app.freezer.shopping.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import app.freezer.catalog.ProductRenamed;
import app.freezer.shopping.domain.ShoppingLists;

/**
 * Keeps the names of products on the shopping list in line with the household's catalog.
 */
@Component
class RenamedProducts {

	private final ShoppingLists lists;

	RenamedProducts(ShoppingLists lists) {
		this.lists = lists;
	}

	@ApplicationModuleListener
	void on(ProductRenamed event) {
		lists.findById(event.householdId()).ifPresent(list -> {
			if (list.productRenamed(event.productId(), event.name())) {
				lists.save(list);
			}
		});
	}

}
