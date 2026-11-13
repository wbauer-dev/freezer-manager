package app.freezer.household.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import app.freezer.household.UserId;

class HouseholdTests {

	static final UserId ALICE = UserId.of("alice");
	static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

	@Test
	void founderBecomesFirstOwner() {
		var household = Household.found("Miller family", ALICE, "Alice", NOW);

		assertThat(household.getMembers()).containsExactly(new Member(ALICE, "Alice", Role.OWNER, NOW));
		assertThat(household.getFoundedAt()).isEqualTo(NOW);
	}

	@Test
	void nameIsTrimmed() {
		var household = Household.found("  Miller family  ", ALICE, "Alice", NOW);

		assertThat(household.getName()).isEqualTo("Miller family");
	}

	@Test
	void rejectsBlankName() {
		assertThatIllegalArgumentException().isThrownBy(() -> Household.found("   ", ALICE, "Alice", NOW));
	}

	@Test
	void rejectsTooLongName() {
		var tooLong = "x".repeat(Household.MAX_NAME_LENGTH + 1);

		assertThatIllegalArgumentException().isThrownBy(() -> Household.found(tooLong, ALICE, "Alice", NOW));
	}

	@Test
	void knowsItsMembers() {
		var household = Household.found("Miller family", ALICE, "Alice", NOW);

		assertThat(household.hasMember(ALICE)).isTrue();
		assertThat(household.hasMember(UserId.of("bob"))).isFalse();
	}

}
