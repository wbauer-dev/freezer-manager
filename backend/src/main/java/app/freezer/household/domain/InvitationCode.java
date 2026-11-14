package app.freezer.household.domain;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import org.jmolecules.ddd.types.Identifier;

/**
 * The secret in an invitation link, e.g. {@code /join/q3J7d0lYzPq8n1Kx4bVt_w}.
 * <p>
 * 128 random bits, base64url-encoded without padding (22 characters), so a code cannot be guessed. Whoever has the
 * link can join the household, which is why invitations are single use and expire.
 */
public record InvitationCode(String value) implements Identifier {

	private static final int RANDOM_BYTES = 16;
	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{22}");
	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * @throws InvalidInvitationException if the value cannot be a code — it is reported like an unknown one
	 */
	public InvitationCode {
		if (value == null || !FORMAT.matcher(value).matches()) {
			throw new InvalidInvitationException();
		}
	}

	public static InvitationCode of(String value) {
		return new InvitationCode(value);
	}

	public static InvitationCode generate() {
		var bytes = new byte[RANDOM_BYTES];
		RANDOM.nextBytes(bytes);
		return new InvitationCode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}

	@Override
	public String toString() {
		return value;
	}

}
