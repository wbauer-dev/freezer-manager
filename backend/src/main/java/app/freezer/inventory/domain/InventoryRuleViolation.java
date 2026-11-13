package app.freezer.inventory.domain;

import app.freezer.sharedkernel.Translatable;

/**
 * A command conflicts with a rule of the inventory given its current state, e.g. taking out more portions than are
 * left. Invalid values are reported with {@link IllegalArgumentException} instead. The message is a message code.
 */
public class InventoryRuleViolation extends RuntimeException implements Translatable {

	private final Object[] arguments;

	public InventoryRuleViolation(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
