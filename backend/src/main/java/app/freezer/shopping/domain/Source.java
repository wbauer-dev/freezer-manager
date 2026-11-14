package app.freezer.shopping.domain;

/**
 * Why an entry is on the shopping list. Staples running low ({@code LOW_STOCK}) and recipes ({@code RECIPE}) will
 * add entries of their own.
 */
public enum Source {

	/** A member added it. */
	MANUAL

}
