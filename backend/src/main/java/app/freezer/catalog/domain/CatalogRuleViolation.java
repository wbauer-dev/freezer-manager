package app.freezer.catalog.domain;

import app.freezer.sharedkernel.Translatable;

/**
 * A change conflicts with the household's catalog, e.g. a name that is already taken. The message is a message code;
 * the web adapter translates it with the arguments.
 */
public class CatalogRuleViolation extends RuntimeException implements Translatable {

	private final Object[] arguments;

	public CatalogRuleViolation(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
