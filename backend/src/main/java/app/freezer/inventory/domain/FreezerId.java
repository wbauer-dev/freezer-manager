package app.freezer.inventory.domain;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a freezer.
 */
public record FreezerId(UUID id) implements Identifier {

	public FreezerId {
		if (id == null) {
			throw new IllegalArgumentException("FreezerId must not be null");
		}
	}

	public static FreezerId of(UUID id) {
		return new FreezerId(id);
	}

	public static FreezerId random() {
		return new FreezerId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
