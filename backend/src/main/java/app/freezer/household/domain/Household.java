package app.freezer.household.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
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
 */
@Table(schema = "household", name = "household")
public class Household implements AggregateRoot<Household, HouseholdId> {

	public static final int MAX_NAME_LENGTH = 50;

	@Identity
	private final HouseholdId id;
	private final String name;
	private final Instant foundedAt;

	@MappedCollection(idColumn = "household_id")
	private final Set<Member> members;

	@Version
	private Long version;

	/** Used by persistence to restore a household; use {@link #found} to create a new one. */
	Household(HouseholdId id, String name, Instant foundedAt, Set<Member> members, Long version) {
		this.id = id;
		this.name = name;
		this.foundedAt = foundedAt;
		this.members = new HashSet<>(members);
		this.version = version;
	}

	/**
	 * Founds a new household. The founder becomes its first owner.
	 */
	public static Household found(String name, UserId founder, String founderName, Instant now) {
		var founderMember = new Member(founder, founderName, Role.OWNER, now);
		return new Household(HouseholdId.random(), validName(name), now, Set.of(founderMember), null);
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
