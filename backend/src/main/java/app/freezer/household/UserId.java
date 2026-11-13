package app.freezer.household;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a user. The value is the subject assigned by the identity provider; it is treated as opaque.
 */
public record UserId(String value) implements Identifier {

	public UserId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("User id must not be blank");
		}
	}

	public static UserId of(String value) {
		return new UserId(value);
	}

	@Override
	public String toString() {
		return value;
	}

}
