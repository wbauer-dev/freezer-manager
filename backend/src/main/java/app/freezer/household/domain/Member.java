package app.freezer.household.domain;

import java.time.Instant;

import org.jmolecules.ddd.types.ValueObject;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.UserId;

/**
 * A user's membership in a household. The display name is a snapshot taken when the user joined; the identity
 * provider remains the source of truth for user data.
 */
@Table(schema = "household", name = "member")
public record Member(UserId userId, String displayName, Role role, Instant joinedAt) implements ValueObject {

	public boolean isOwner() {
		return role == Role.OWNER;
	}

}
