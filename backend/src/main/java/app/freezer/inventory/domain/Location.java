package app.freezer.inventory.domain;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Where a frozen item is stored. Obtained from {@link Freezer#locationOf(CompartmentId)}, so it always names an
 * existing compartment of that freezer.
 */
public record Location(FreezerId freezerId, CompartmentId compartmentId) implements ValueObject {

	public Location {
		if (freezerId == null || compartmentId == null) {
			throw new IllegalArgumentException("A location needs a freezer and a compartment");
		}
	}

}
