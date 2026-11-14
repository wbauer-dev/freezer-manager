package app.freezer.inventory.application;

import java.time.LocalDate;

import app.freezer.catalog.ProductId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.ExpiryStatus;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.media.PhotoId;

/**
 * One line of the eat-first list. The freezer and compartment ids let pages group the items by where they are.
 */
public record EatFirstEntry(FrozenItemId itemId, ProductId productId, PhotoId photoId, String productName, String note,
		int remainingPortions, Integer gramsPerPortion, LocalDate frozenOn, LocalDate bestBefore,
		ExpiryStatus expiryStatus, FreezerId freezerId, String freezerName, CompartmentId compartmentId,
		String compartmentName) {

}
