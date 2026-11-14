package app.freezer.household.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.household.HouseholdNotFoundException;
import app.freezer.household.UserId;
import app.freezer.household.domain.Household;
import app.freezer.household.domain.Households;
import app.freezer.household.domain.InvalidInvitationException;
import app.freezer.household.domain.Invitation;
import app.freezer.household.domain.InvitationCode;

/**
 * Use cases around households, their members and invitations. The rules — who may do what, at least one owner — are
 * the {@link Household}'s.
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

	public Invitation invite(HouseholdMember owner) {
		var household = householdOf(owner);
		var invitation = household.invite(owner.userId(), Instant.now(clock));
		households.save(household);
		return invitation;
	}

	@Transactional(readOnly = true)
	public List<Invitation> openInvitations(HouseholdMember owner) {
		return householdOf(owner).openInvitations(owner.userId(), Instant.now(clock));
	}

	public void revokeInvitation(HouseholdMember owner, InvitationCode code) {
		var household = householdOf(owner);
		household.revokeInvitation(code, owner.userId());
		households.save(household);
	}

	/**
	 * The user joins the household the invitation is for — or, already being a member, simply stays one.
	 *
	 * @return the household the user is now a member of
	 * @throws InvalidInvitationException if the code is unknown, expired, used or revoked
	 */
	public Household acceptInvitation(InvitationCode code, CurrentUser user) {
		var household = households.findByInvitation(code).orElseThrow(InvalidInvitationException::new);
		household.accept(code, user.id(), user.displayName(), Instant.now(clock));
		return households.save(household);
	}

	public Household makeOwner(HouseholdMember owner, UserId member) {
		var household = householdOf(owner);
		household.makeOwner(member, owner.userId());
		return households.save(household);
	}

	public Household removeMember(HouseholdMember owner, UserId member) {
		var household = householdOf(owner);
		household.remove(member, owner.userId());
		return households.save(household);
	}

	public void leave(HouseholdMember member) {
		var household = householdOf(member);
		household.leave(member.userId());
		households.save(household);
	}

	private Household householdOf(HouseholdMember member) {
		return households.findById(member.householdId())
				.orElseThrow(() -> new HouseholdNotFoundException(member.householdId()));
	}

}
