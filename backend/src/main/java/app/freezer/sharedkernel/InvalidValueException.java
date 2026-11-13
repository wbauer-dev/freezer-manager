package app.freezer.sharedkernel;

/**
 * An invalid value whose message code needs arguments, e.g. the maximum length of a name.
 */
public class InvalidValueException extends IllegalArgumentException implements Translatable {

	private final Object[] arguments;

	public InvalidValueException(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
