package app.freezer.labeling.application;

/**
 * The label does not exist or belongs to a household the user is not a member of.
 */
public class UnknownLabelException extends RuntimeException {

	public UnknownLabelException() {
		super("labeling.label.unknown");
	}

	/**
	 * Keeps the reason for diagnostics, e.g. that the user is not a member of the label's household or that the code
	 * cannot be a label code at all.
	 */
	public UnknownLabelException(Throwable cause) {
		super("labeling.label.unknown", cause);
	}

}
