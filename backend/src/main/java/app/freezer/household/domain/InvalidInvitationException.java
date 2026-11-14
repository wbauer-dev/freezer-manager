package app.freezer.household.domain;

/**
 * An invitation code that cannot be used: unknown, expired, already used or revoked. All cases are reported the same
 * way, so nobody can find out more about a code than that it does not work.
 */
public class InvalidInvitationException extends RuntimeException {

	public InvalidInvitationException() {
		super("household.invitation.invalid");
	}

}
