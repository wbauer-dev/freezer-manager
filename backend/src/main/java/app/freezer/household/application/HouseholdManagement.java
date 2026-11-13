package app.freezer.household.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdNotFoundException;
import app.freezer.household.domain.Household;
import app.freezer.household.domain.Households;

/**
 * Use cases around households.
 */
@Service
@Transactional
public class HouseholdManagement {

	private final Households households;
	private final Clock clock;

	HouseholdManagement(Households households, Clock clock) {
		this.households = households;
		this.clock = clock;
	}

	public Household foundHousehold(String name, CurrentUser user) {
		var household = Household.found(name, user.id(), user.displayName(), Instant.now(clock));
		return households.save(household);
	}

	@Transactional(readOnly = true)
	public List<Household> householdsOf(CurrentUser user) {
		return households.findAllWithMember(user.id());
	}

	@Transactional(readOnly = true)
	public Household household(HouseholdId id, CurrentUser user) {
		return households.findById(id)
				.filter(household -> household.hasMember(user.id()))
				.orElseThrow(() -> new HouseholdNotFoundException(id));
	}

}
