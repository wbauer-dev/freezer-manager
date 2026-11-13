package app.freezer.sharedkernel;

/**
 * An exception whose message is a message code, e.g. {@code inventory.item.not-in-freezer}. The web adapters show it
 * in the user's language and fill the code's placeholders with the arguments. The domain stays free of texts.
 */
public interface Translatable {

	/** The message code. */
	String getMessage();

	Object[] getArguments();

}
