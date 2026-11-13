package app.freezer.inventory.domain;

/**
 * Whether a frozen item is still in the freezer, or how it left it.
 */
public enum ItemState {

	IN_FREEZER,

	/** The last portion was taken out. */
	CONSUMED,

	/** Removed without being eaten. */
	DISCARDED

}
