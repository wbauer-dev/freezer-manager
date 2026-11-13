package app.freezer.catalog.domain;

import app.freezer.sharedkernel.InvalidValueException;

/**
 * Validation of the names members give categories and products.
 */
final class Names {

	static final int MAX_LENGTH = 40;

	private Names() {
	}

	/** @param what message code prefix, e.g. {@code catalog.product} */
	static String valid(String name, String what) {
		var trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(what + ".name-blank");
		}
		if (trimmed.length() > MAX_LENGTH) {
			throw new InvalidValueException(what + ".name-too-long", MAX_LENGTH);
		}
		return trimmed;
	}

}
