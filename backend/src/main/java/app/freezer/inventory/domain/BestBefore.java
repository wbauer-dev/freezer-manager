package app.freezer.inventory.domain;

import java.time.LocalDate;
import java.time.Period;

import org.jmolecules.ddd.types.ValueObject;

/**
 * The date by which a frozen item should be eaten — either calculated from the shelf life of the product's food
 * category or set by a member.
 */
public record BestBefore(LocalDate date, boolean overridden) implements ValueObject {

	/**
	 * Items whose best-before date is this close count as "expiring soon". Will become a household setting.
	 */
	public static final Period WARNING_PERIOD = Period.ofDays(14);

	public BestBefore {
		if (date == null) {
			throw new IllegalArgumentException("Best-before date must not be null");
		}
	}

	/** The shelf-life policy: frozen on + recommended shelf life of the food category. */
	public static BestBefore calculate(LocalDate frozenOn, Period shelfLife) {
		return new BestBefore(frozenOn.plus(shelfLife), false);
	}

	public static BestBefore overriddenWith(LocalDate date) {
		return new BestBefore(date, true);
	}

	public ExpiryStatus statusOn(LocalDate today) {
		if (date.isBefore(today)) {
			return ExpiryStatus.EXPIRED;
		}
		if (!date.isAfter(today.plus(WARNING_PERIOD))) {
			return ExpiryStatus.EXPIRING_SOON;
		}
		return ExpiryStatus.FRESH;
	}

}
