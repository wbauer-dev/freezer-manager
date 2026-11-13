package app.freezer.household;

/**
 * Checks household membership for all modules.
 */
public interface HouseholdAccess {

	/**
	 * @throws HouseholdNotFoundException if the household does not exist or the user is not a member
	 */
	HouseholdMember requireMember(HouseholdId householdId, CurrentUser user);

}
