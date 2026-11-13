package app.freezer.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;

import org.junit.jupiter.api.Test;

class BestBeforeTests {

	static final BestBefore FEB_1 = BestBefore.overriddenWith(LocalDate.of(2027, 2, 1));

	@Test
	void isCalculatedFromTheShelfLife() {
		var bestBefore = BestBefore.calculate(LocalDate.of(2026, 11, 1), Period.ofMonths(3));

		assertThat(bestBefore).isEqualTo(new BestBefore(LocalDate.of(2027, 2, 1), false));
	}

	@Test
	void isFreshMoreThanTwoWeeksAhead() {
		assertThat(FEB_1.statusOn(LocalDate.of(2027, 1, 17))).isEqualTo(ExpiryStatus.FRESH);
	}

	@Test
	void isExpiringSoonWithinTwoWeeksIncludingTheLastDay() {
		assertThat(FEB_1.statusOn(LocalDate.of(2027, 1, 18))).isEqualTo(ExpiryStatus.EXPIRING_SOON);
		assertThat(FEB_1.statusOn(LocalDate.of(2027, 2, 1))).isEqualTo(ExpiryStatus.EXPIRING_SOON);
	}

	@Test
	void isExpiredTheDayAfter() {
		assertThat(FEB_1.statusOn(LocalDate.of(2027, 2, 2))).isEqualTo(ExpiryStatus.EXPIRED);
	}

}
