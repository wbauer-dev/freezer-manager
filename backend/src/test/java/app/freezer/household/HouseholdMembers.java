package app.freezer.household;

/**
 * Test fixture: lets tests of other modules create a {@link HouseholdMember} without going through
 * {@link HouseholdAccess}. Lives in the household package because the constructor is package-private.
 */
public final class HouseholdMembers {

	private HouseholdMembers() {
	}

	public static HouseholdMember member(HouseholdId householdId, UserId userId, String displayName) {
		return new HouseholdMember(householdId, userId, displayName);
	}

}
