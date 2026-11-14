package app.freezer.household.domain;

import app.freezer.sharedkernel.Translatable;

/**
 * A command conflicts with a rule of the household given its current state, e.g. the last owner leaving. Invalid
 * values are reported with {@link IllegalArgumentException} instead. The message is a message code.
 */
public class HouseholdRuleViolation extends RuntimeException implements Translatable {

	private final Object[] arguments;

	public HouseholdRuleViolation(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
