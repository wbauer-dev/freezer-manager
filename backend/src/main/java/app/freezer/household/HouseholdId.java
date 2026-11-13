package app.freezer.household;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a household. Other modules use it as tenant key: every aggregate belongs to exactly one household.
 */
public record HouseholdId(UUID id) implements Identifier {

	public HouseholdId {
		if (id == null) {
			throw new IllegalArgumentException("Household id must not be null");
		}
	}

	public static HouseholdId of(UUID id) {
		return new HouseholdId(id);
	}

	public static HouseholdId random() {
		return new HouseholdId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
