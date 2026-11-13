package app.freezer.inventory.domain;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a compartment within a freezer.
 */
public record CompartmentId(UUID id) implements Identifier {

	public CompartmentId {
		if (id == null) {
			throw new IllegalArgumentException("CompartmentId must not be null");
		}
	}

	public static CompartmentId of(UUID id) {
		return new CompartmentId(id);
	}

	public static CompartmentId random() {
		return new CompartmentId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
