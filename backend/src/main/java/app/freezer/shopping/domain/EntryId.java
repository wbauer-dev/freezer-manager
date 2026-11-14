package app.freezer.shopping.domain;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies an entry within a shopping list.
 */
public record EntryId(UUID id) implements Identifier {

	public EntryId {
		if (id == null) {
			throw new IllegalArgumentException("EntryId must not be null");
		}
	}

	public static EntryId of(UUID id) {
		return new EntryId(id);
	}

	public static EntryId random() {
		return new EntryId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
