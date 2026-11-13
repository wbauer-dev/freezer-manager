package app.freezer.household;

/**
 * The household does not exist or the current user is not a member. Both cases are reported the same way, so the
 * API does not reveal which households exist.
 */
public class HouseholdNotFoundException extends RuntimeException {

	public HouseholdNotFoundException(HouseholdId id) {
		super("Household " + id + " not found");
	}

	/** Keeps the reason for diagnostics, e.g. a household id that could not be parsed. */
	public HouseholdNotFoundException(HouseholdId id, Throwable cause) {
		super("Household " + id + " not found", cause);
	}

}
