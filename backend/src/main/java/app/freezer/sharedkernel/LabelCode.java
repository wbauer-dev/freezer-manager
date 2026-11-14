package app.freezer.sharedkernel;

import java.security.SecureRandom;
import java.util.Locale;

import org.jmolecules.ddd.types.Identifier;

/**
 * The code on a QR label, e.g. {@code K7Q2M9XA}, printed as {@code K7Q2-M9XA}.
 * <p>
 * Eight characters of Crockford's Base32 (about 40 bits): no I, L, O or U, so people can read and type a code without
 * mixing up characters.
 */
public record LabelCode(String value) implements Identifier {

	public static final int LENGTH = 8;
	/** Message code for anything that is not a label code; its text is in the common bundle. */
	private static final String INVALID = "sharedkernel.label-code.invalid";
	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	private static final SecureRandom RANDOM = new SecureRandom();

	public LabelCode {
		if (value == null || value.length() != LENGTH || !value.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
			throw new InvalidValueException(INVALID, LENGTH);
		}
	}

	public static LabelCode of(String value) {
		return new LabelCode(value);
	}

	public static LabelCode generate() {
		var code = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return new LabelCode(code.toString());
	}

	/**
	 * Reads a code as people type it: case and dashes don't matter, and the letters Crockford's alphabet leaves out are
	 * read as the digits they look like (I and L as 1, O as 0).
	 */
	public static LabelCode parse(String text) {
		if (text == null) {
			throw new InvalidValueException(INVALID, LENGTH);
		}
		var normalized = text.toUpperCase(Locale.ROOT)
				.replaceAll("[\\s-]", "")
				.replace('I', '1')
				.replace('L', '1')
				.replace('O', '0');
		return new LabelCode(normalized);
	}

	/** As printed on the label: {@code K7Q2-M9XA}. */
	public String formatted() {
		return value.substring(0, 4) + "-" + value.substring(4);
	}

	@Override
	public String toString() {
		return value;
	}

}
