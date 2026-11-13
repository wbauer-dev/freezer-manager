package app.freezer.media.application;

import app.freezer.sharedkernel.Translatable;

/**
 * The upload is no usable photo. The message is a message code.
 */
public class InvalidImageException extends RuntimeException implements Translatable {

	private final Object[] arguments;

	public InvalidImageException(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
