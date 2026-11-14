package app.freezer.shopping.domain;

import app.freezer.sharedkernel.Translatable;

/**
 * A command conflicts with the current state of the shopping list. The message is a message code.
 */
public class ShoppingRuleViolation extends RuntimeException implements Translatable {

	private final Object[] arguments;

	public ShoppingRuleViolation(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
