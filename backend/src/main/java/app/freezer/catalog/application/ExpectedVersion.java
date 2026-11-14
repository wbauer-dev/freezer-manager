package app.freezer.catalog.application;

/**
 * The version of a category or product that a member last saw, sent back with a change to it: an optimistic
 * concurrency token. A change to a version that is no longer current would silently overwrite what another member
 * changed in the meantime, so it is rejected.
 * <p>
 * The token belongs to the conversation with the member, not to the catalog's rules, so it is checked here in the
 * application layer and not in the aggregates. It covers the time the member was looking at the item; the moment
 * between loading and saving is guarded by the aggregate's {@code @Version}, as for every aggregate.
 *
 * @param value the version as the member saw it
 */
public record ExpectedVersion(long value) {

	public static ExpectedVersion of(long value) {
		return new ExpectedVersion(value);
	}

	/**
	 * @param current     the version as it is now
	 * @param changedCode the message code explaining what changed, e.g. {@code catalog.product.changed}
	 * @throws StaleVersionException if the version the member saw is no longer current
	 */
	void requireCurrent(long current, String changedCode) {
		if (value != current) {
			throw new StaleVersionException(changedCode);
		}
	}

}
