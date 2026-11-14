package app.freezer.inventory.application;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import app.freezer.catalog.Catalog;
import app.freezer.catalog.ProductDescription;
import app.freezer.catalog.ProductRenamed;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.domain.FrozenItems;

/**
 * Keeps the names of items in the freezer in line with the household's catalog.
 * <p>
 * The name is taken from the catalog, not from the event: an event that arrives late or a second time must not bring
 * back an older name. Each item is renamed in a transaction of its own, because members keep taking portions out
 * meanwhile. A rename that collided with such a change is applied again to the fresh item, like the shopping list does
 * with its commands; one item that keeps colliding does not hold up the others.
 */
@Component
class CatalogChanges {

	static final int ATTEMPTS = 3;

	private final FrozenItems items;
	private final Catalog catalog;
	private final TransactionTemplate transaction;

	CatalogChanges(FrozenItems items, Catalog catalog, PlatformTransactionManager transactions) {
		this.items = items;
		this.catalog = catalog;
		this.transaction = new TransactionTemplate(transactions);
	}

	/** Runs outside a transaction; {@link #rename} opens one per item. */
	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ProductRenamed event) {
		// A product removed from the catalog keeps the last name it was given.
		var name = catalog.findProduct(event.householdId(), event.productId())
				.map(ProductDescription::name)
				.orElse(event.name());
		RuntimeException unfinished = null;
		for (var item : items.findInFreezerByProduct(event.productId())) {
			if (item.belongsTo(event.householdId())) {
				try {
					rename(item.getId(), name);
				}
				catch (RuntimeException e) {
					unfinished = e;
				}
			}
		}
		if (unfinished != null) {
			// The event stays incomplete and is resubmitted on restart; items renamed by then are left as they are.
			throw unfinished;
		}
	}

	private void rename(FrozenItemId itemId, String name) {
		for (int attempt = 1;; attempt++) {
			try {
				transaction.executeWithoutResult(status -> items.findById(itemId).ifPresent(item -> {
					if (item.productRenamed(name)) {
						items.save(item);
					}
				}));
				return;
			}
			catch (RuntimeException e) {
				if (!isConcurrentChange(e) || attempt == ATTEMPTS) {
					throw e;
				}
			}
		}
	}

	private static boolean isConcurrentChange(Throwable e) {
		for (var cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof OptimisticLockingFailureException) {
				return true;
			}
		}
		return false;
	}

}
