package app.freezer.inventory.domain;

/**
 * How urgently an item should be eaten, relative to a given day.
 */
public enum ExpiryStatus {

	/** Best-before date has passed. */
	EXPIRED,

	/** Best-before date is within the warning period. */
	EXPIRING_SOON,

	FRESH

}
