package app.freezer.inventory.domain;

import org.jmolecules.ddd.types.ValueObject;

import app.freezer.sharedkernel.InvalidValueException;

/**
 * How many portions a frozen item had when it was frozen and how many are left. Immutable: taking out returns new
 * {@code Portions}.
 */
public record Portions(int initial, int remaining) implements ValueObject {

	public static final int MAX = 99;

	public Portions {
		if (initial < 1 || initial > MAX) {
			throw new InvalidValueException("inventory.portions.invalid", MAX);
		}
		if (remaining < 0 || remaining > initial) {
			throw new InvalidValueException("inventory.portions.remaining-invalid", initial);
		}
	}

	public static Portions of(int count) {
		return new Portions(count, count);
	}

	public Portions takeOut(int count) {
		if (count < 1) {
			throw new IllegalArgumentException("inventory.take-out.at-least-one");
		}
		if (count > remaining) {
			throw new InventoryRuleViolation("inventory.take-out.too-many", remaining);
		}
		return new Portions(initial, remaining - count);
	}

	public Portions putBack(int count) {
		return new Portions(initial, remaining + count);
	}

	public boolean isEmpty() {
		return remaining == 0;
	}

}
