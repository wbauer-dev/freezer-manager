package app.freezer.catalog.application;

/**
 * A member changed a category or product in a version that is no longer current: another member changed it in the
 * meantime (see {@link ExpectedVersion}). The message is a message code.
 */
public class StaleVersionException extends RuntimeException {

	public StaleVersionException(String code) {
		super(code);
	}

}
