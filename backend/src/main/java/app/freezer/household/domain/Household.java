package app.freezer.household.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.sharedkernel.InvalidValueException;

/**
 * A group of members sharing freezers and a shopping list. All other data belongs to exactly one household.
 * <p>
 * Rules:
 * <ul>
 * <li>A household has at least one owner at all times. The last owner makes another member an owner before leaving;
 * the only member cannot leave (a household is never left empty).</li>
 * <li>Only owners invite, see and revoke invitations, remove members and make members owners.</li>
 * <li>An invitation is used once and expires after {@link Invitation#VALIDITY}. Someone who is already a member
 * stays one and leaves the invitation open for whoever it was meant for.</li>
 * </ul>
 */
@Table(schema = "household", name = "household")
public class Household implements AggregateRoot<Household, HouseholdId> {

	public static final int MAX_NAME_LENGTH = 50;
	/** Invitations are loaded and saved with the household; the limit keeps it small. */
	public static final int MAX_OPEN_INVITATIONS = 20;

	@Identity
	private final HouseholdId id;
	private final String name;
	private final Instant foundedAt;

	@MappedCollection(idColumn = "household_id")
	private final Set<Member> members;

	@MappedCollection(idColumn = "household_id")
	private final Set<Invitation> invitations;

	@Version
	private Long version;

	/** Used by persistence to restore a household; use {@link #found} to create a new one. */
	Household(HouseholdId id, String name, Instant foundedAt, Set<Member> members, Set<Invitation> invitations,
			Long version) {
		this.id = id;
		this.name = name;
		this.foundedAt = foundedAt;
		this.members = new HashSet<>(members);
		this.invitations = new HashSet<>(invitations);
		this.version = version;
	}

	/**
	 * Founds a new household. The founder becomes its first owner.
	 */
	public static Household found(String name, UserId founder, String founderName, Instant now) {
		var founderMember = new Member(founder, founderName, Role.OWNER, now);
		return new Household(HouseholdId.random(), validName(name), now, Set.of(founderMember), Set.of(), null);
	}

	/**
	 * An owner invites someone: whoever gets the invitation's link can join once within {@link Invitation#VALIDITY}.
	 * Expired invitations are cleared away.
	 */
	public Invitation invite(UserId by, Instant now) {
		requireOwner(by);
		invitations.removeIf(invitation -> !invitation.isValidAt(now));
		if (invitations.size() >= MAX_OPEN_INVITATIONS) {
			throw new HouseholdRuleViolation("household.invitation.too-many", MAX_OPEN_INVITATIONS);
		}
		var invitation = Invitation.issue(now);
		invitations.add(invitation);
		return invitation;
	}

	/**
	 * The invitations that can still be used, the newest first. Only owners see them: whoever has a code can join.
	 */
	public List<Invitation> openInvitations(UserId asSeenBy, Instant now) {
		requireOwner(asSeenBy);
		return invitations.stream()
				.filter(invitation -> invitation.isValidAt(now))
				.sorted(Comparator.comparing(Invitation::createdAt).reversed())
				.toList();
	}

	/**
	 * Takes an invitation back. Revoking one that is gone already (used, revoked by another owner) changes nothing.
	 */
	public void revokeInvitation(InvitationCode code, UserId by) {
		requireOwner(by);
		invitations.removeIf(invitation -> invitation.code().equals(code));
	}

	/**
	 * The user joins as a member and the invitation is used up. A member who opens the link again simply stays a
	 * member; the invitation is left for whoever it was meant for.
	 *
	 * @throws InvalidInvitationException if this household has no such invitation or it has expired
	 */
	public void accept(InvitationCode code, UserId user, String displayName, Instant now) {
		if (hasMember(user)) {
			return;
		}
		var invitation = invitations.stream()
				.filter(candidate -> candidate.code().equals(code) && candidate.isValidAt(now))
				.findFirst()
				.orElseThrow(InvalidInvitationException::new);
		invitations.remove(invitation);
		members.add(new Member(user, displayName, Role.MEMBER, now));
	}

	/**
	 * An owner makes another member an owner too, e.g. to hand the household over before leaving.
	 */
	public void makeOwner(UserId member, UserId by) {
		requireOwner(by);
		var current = member(member).orElseThrow(() -> new HouseholdRuleViolation("household.member.unknown"));
		if (!current.isOwner()) {
			members.remove(current);
			members.add(current.asOwner());
		}
	}

	/**
	 * An owner removes a member. Removing someone who is gone already changes nothing; removing yourself is
	 * {@linkplain #leave leaving}.
	 */
	public void remove(UserId member, UserId by) {
		if (member.equals(by)) {
			leave(by);
			return;
		}
		requireOwner(by);
		member(member).ifPresent(members::remove);
	}

	/**
	 * A member leaves the household. The last owner has to make someone else an owner first, and the only member
	 * cannot leave at all.
	 */
	public void leave(UserId user) {
		var leaving = member(user);
		if (leaving.isEmpty()) {
			return;
		}
		if (members.size() == 1) {
			throw new HouseholdRuleViolation("household.member.only-member");
		}
		if (leaving.get().isOwner() && members.stream().filter(Member::isOwner).count() == 1) {
			throw new HouseholdRuleViolation("household.member.last-owner");
		}
		members.remove(leaving.get());
	}

	public boolean hasMember(UserId userId) {
		return member(userId).isPresent();
	}

	public Optional<Member> member(UserId userId) {
		return members.stream().filter(member -> member.userId().equals(userId)).findFirst();
	}

	@Override
	public HouseholdId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Instant getFoundedAt() {
		return foundedAt;
	}

	public Set<Member> getMembers() {
		return Collections.unmodifiableSet(members);
	}

	private void requireOwner(UserId userId) {
		if (!member(userId).map(Member::isOwner).orElse(false)) {
			throw new OwnerRequiredException();
		}
	}

	private static String validName(String name) {
		var trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("household.name-blank");
		}
		if (trimmed.length() > MAX_NAME_LENGTH) {
			throw new InvalidValueException("household.name-too-long", MAX_NAME_LENGTH);
		}
		return trimmed;
	}

}
