package app.freezer.labeling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.sharedkernel.LabelCode;

class LabelTests {

	static final HouseholdId HOUSEHOLD = HouseholdId.random();
	static final FrozenItemId PEAS = FrozenItemId.random();
	static final FrozenItemId GOULASH = FrozenItemId.random();
	static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

	@Test
	void oneTimeLabelIsUsedUpWithItsItem() {
		var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, NOW);

		label.release(PEAS);

		assertThat(label.getState()).isEqualTo(LabelState.USED_UP);
	}

	@Test
	void persistentLabelIsFreeAgainForTheNextFilling() {
		var label = Label.persistent(HOUSEHOLD, NOW);

		label.attach(GOULASH);
		assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);

		label.release(GOULASH);
		assertThat(label.getState()).isEqualTo(LabelState.FREE);

		label.attach(PEAS);
		assertThat(label.getItemId()).contains(PEAS);
	}

	@Test
	void attachedLabelCannotGoOnAnotherItem() {
		var label = Label.persistent(HOUSEHOLD, NOW);
		label.attach(GOULASH);

		assertThatThrownBy(() -> label.attach(PEAS)).isInstanceOf(LabelingRuleViolation.class);
	}

	@Test
	void attachingAgainToTheSameItemChangesNothing() {
		var oneTime = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, NOW);
		oneTime.attach(PEAS);
		assertThat(oneTime.getItemId()).contains(PEAS);

		oneTime.release(PEAS);
		assertThatThrownBy(() -> oneTime.attach(PEAS)).isInstanceOf(LabelingRuleViolation.class);
	}

	@Test
	void releaseForAnotherItemIsIgnored() {
		var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, NOW);

		label.release(GOULASH);

		assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);
	}

	@Test
	void undoAttachesTheLabelAgainUnlessItMovedOn() {
		var oneTime = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, NOW);
		oneTime.release(PEAS);
		oneTime.reattach(PEAS);
		assertThat(oneTime.getState()).isEqualTo(LabelState.ATTACHED);

		var persistent = Label.persistent(HOUSEHOLD, NOW);
		persistent.attach(PEAS);
		persistent.release(PEAS);
		persistent.attach(GOULASH);
		persistent.reattach(PEAS);
		assertThat(persistent.getItemId()).contains(GOULASH);
	}

}
