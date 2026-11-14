package app.freezer.shopping.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import app.freezer.catalog.ProductRenamed;

/**
 * Keeps the names of products on the shopping list in line with the household's catalog.
 */
@Component
class RenamedProducts {

	private final ShoppingListManagement shopping;

	RenamedProducts(ShoppingListManagement shopping) {
		this.shopping = shopping;
	}

	/**
	 * Runs without a transaction of its own: {@link ShoppingListManagement} opens one per attempt, so a rename that
	 * collided with a member's change is applied again right away instead of waiting for the event to be resubmitted.
	 */
	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ProductRenamed event) {
		shopping.productRenamed(event.householdId(), event.productId(), event.name());
	}

}
