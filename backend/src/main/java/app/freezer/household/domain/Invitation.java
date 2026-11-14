package app.freezer.household.domain;

import java.time.Duration;
import java.time.Instant;

import org.jmolecules.ddd.types.Entity;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An open invitation to join a {@link Household}, part of that aggregate. Whoever has its code can join once, until
 * it expires. A used or revoked invitation is gone.
 */
@Table(schema = "household", name = "invitation")
public record Invitation(InvitationCode code, Instant createdAt, Instant expiresAt)
		implements Entity<Household, InvitationCode> {

	public static final Duration VALIDITY = Duration.ofDays(7);

	static Invitation issue(Instant now) {
		return new Invitation(InvitationCode.generate(), now, now.plus(VALIDITY));
	}

	public boolean isValidAt(Instant now) {
		return now.isBefore(expiresAt);
	}

	@Override
	public InvitationCode getId() {
		return code;
	}

}
