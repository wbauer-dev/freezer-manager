package app.freezer.household;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.domain.Households;

/**
 * Lives in the API package because it is the only place allowed to create {@link HouseholdMember} instances.
 */
@Service
@Transactional(readOnly = true)
class MembershipCheck implements HouseholdAccess {

	private final Households households;

	MembershipCheck(Households households) {
		this.households = households;
	}

	@Override
	public HouseholdMember requireMember(HouseholdId householdId, CurrentUser user) {
		return households.findById(householdId)
				.flatMap(household -> household.member(user.id()))
				.map(member -> new HouseholdMember(householdId, member.userId(), user.displayName()))
				.orElseThrow(() -> new HouseholdNotFoundException(householdId));
	}

}
