package app.freezer.inventory;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a frozen item. Part of the published API because events refer to it.
 */
public record FrozenItemId(UUID id) implements Identifier {

	public FrozenItemId {
		if (id == null) {
			throw new IllegalArgumentException("FrozenItemId must not be null");
		}
	}

	public static FrozenItemId of(UUID id) {
		return new FrozenItemId(id);
	}

	public static FrozenItemId random() {
		return new FrozenItemId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
