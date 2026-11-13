package app.freezer.household;

import java.util.Objects;

/**
 * Proof that a user is a member of a household.
 * <p>
 * Only {@link HouseholdAccess} can create instances (the constructor is package-private). Use cases of other modules
 * take a {@code HouseholdMember} as parameter, so they cannot run for users outside the household. Controllers
 * declare it as a method parameter; it is resolved from the {@code {householdId}} path variable and the access token.
 */
public final class HouseholdMember {

	private final HouseholdId householdId;
	private final UserId userId;
	private final String displayName;

	HouseholdMember(HouseholdId householdId, UserId userId, String displayName) {
		this.householdId = Objects.requireNonNull(householdId);
		this.userId = Objects.requireNonNull(userId);
		this.displayName = Objects.requireNonNull(displayName);
	}

	public HouseholdId householdId() {
		return householdId;
	}

	public UserId userId() {
		return userId;
	}

	public String displayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return "HouseholdMember[" + userId + " of " + householdId + "]";
	}

}
