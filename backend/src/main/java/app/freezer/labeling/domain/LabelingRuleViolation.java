package app.freezer.labeling.domain;

import app.freezer.sharedkernel.Translatable;

/**
 * A command conflicts with the current state of labels or the sheet. The message is a message code.
 */
public class LabelingRuleViolation extends RuntimeException implements Translatable {

	private final Object[] arguments;

	public LabelingRuleViolation(String code, Object... arguments) {
		super(code);
		this.arguments = arguments.clone();
	}

	@Override
	public Object[] getArguments() {
		return arguments.clone();
	}

}
