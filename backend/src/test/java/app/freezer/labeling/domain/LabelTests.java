package app.freezer.labeling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.sharedkernel.LabelCode;

class LabelTests {

	static final HouseholdId HOUSEHOLD = HouseholdId.random();
	static final FrozenItemId PEAS = FrozenItemId.random();
	static final FrozenItemId GOULASH = FrozenItemId.random();
	static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

	// Revisions of an item's events, as Inventory stamps them: frozen, portions taken out, consumed, take-out undone.
	static final long FROZEN = 1;
	static final long CONSUMED = 3;
	static final long UNDONE = 4;

	@Test
	void oneTimeLabelIsUsedUpWithItsItem() {
		var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);

		label.release(PEAS, CONSUMED);

		assertThat(label.getState()).isEqualTo(LabelState.USED_UP);
	}

	@Test
	void persistentLabelIsFreeAgainForTheNextFilling() {
		var label = Label.persistent(HOUSEHOLD, NOW);

		label.attach(GOULASH, FROZEN);
		assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);

		label.release(GOULASH, CONSUMED);
		assertThat(label.getState()).isEqualTo(LabelState.FREE);

		label.attach(PEAS, FROZEN);
		assertThat(label.getItemId()).contains(PEAS);
		assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);
	}

	@Test
	void attachedLabelCannotGoOnAnotherItem() {
		var label = Label.persistent(HOUSEHOLD, NOW);
		label.attach(GOULASH, FROZEN);

		assertThatThrownBy(() -> label.attach(PEAS, FROZEN)).isInstanceOf(LabelingRuleViolation.class);
	}

	@Test
	void usedUpLabelCannotGoOnAnotherItem() {
		var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
		label.release(PEAS, CONSUMED);

		assertThatThrownBy(() -> label.attach(GOULASH, FROZEN)).isInstanceOf(LabelingRuleViolation.class)
				.hasMessage("labeling.label.used-up");
	}

	@Test
	void attachingAgainToTheSameItemChangesNothing() {
		var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);

		label.attach(PEAS, FROZEN);

		assertThat(label.getItemId()).contains(PEAS);
		assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);
	}

	@Test
	void releaseForAnotherItemIsIgnored() {
		var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);

		label.release(GOULASH, CONSUMED);

		assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);
	}

	@Test
	void undoAttachesTheLabelAgainUnlessItMovedOn() {
		var oneTime = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
		oneTime.release(PEAS, CONSUMED);
		oneTime.reattach(PEAS, UNDONE);
		assertThat(oneTime.getState()).isEqualTo(LabelState.ATTACHED);

		var persistent = Label.persistent(HOUSEHOLD, NOW);
		persistent.attach(PEAS, FROZEN);
		persistent.release(PEAS, CONSUMED);
		persistent.attach(GOULASH, FROZEN);
		persistent.reattach(PEAS, UNDONE);
		assertThat(persistent.getItemId()).contains(GOULASH);
	}

	/** Events arrive late and out of order: one that failed is retried after newer ones were applied. */
	@Nested
	class StaleEvents {

		@Test
		void anOlderReleaseAfterAnUndoIsIgnored() {
			var oneTime = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
			var persistent = Label.persistent(HOUSEHOLD, NOW);
			persistent.attach(PEAS, FROZEN);

			for (var label : new Label[] { oneTime, persistent }) {
				// "Consumed" failed at first; the undo came through while the label was still attached.
				label.reattach(PEAS, UNDONE);
				label.release(PEAS, CONSUMED);

				assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);
				assertThat(label.getItemId()).contains(PEAS);
			}
		}

		@Test
		void anOlderUndoAfterTheItemLeftForGoodIsIgnored() {
			var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
			label.release(PEAS, CONSUMED);
			label.reattach(PEAS, UNDONE);
			label.release(PEAS, 6); // discarded later

			label.reattach(PEAS, UNDONE);

			assertThat(label.getState()).isEqualTo(LabelState.USED_UP);
		}

		@Test
		void anOlderFreezeIsIgnoredOnceTheItemIsGone() {
			var label = Label.persistent(HOUSEHOLD, NOW);
			label.attach(PEAS, FROZEN);
			label.release(PEAS, CONSUMED);

			label.attach(PEAS, FROZEN);

			assertThat(label.getState()).isEqualTo(LabelState.FREE);
		}

		@Test
		void aNewerEventIsApplied() {
			var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
			label.reattach(PEAS, 2); // an undo that changed nothing for the label

			label.release(PEAS, CONSUMED);

			assertThat(label.getState()).isEqualTo(LabelState.USED_UP);
		}

		@Test
		void theSameRevisionIsNotAppliedTwice() {
			var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
			label.release(PEAS, CONSUMED);
			label.reattach(PEAS, UNDONE);

			label.release(PEAS, CONSUMED);

			assertThat(label.getState()).isEqualTo(LabelState.ATTACHED);
		}

		@Test
		void revisionsCountPerItem() {
			var label = Label.persistent(HOUSEHOLD, NOW);
			label.attach(GOULASH, FROZEN);
			label.release(GOULASH, 9);

			label.attach(PEAS, FROZEN);
			label.release(PEAS, CONSUMED);

			assertThat(label.getState()).isEqualTo(LabelState.FREE);
			assertThat(label.getItemId()).contains(PEAS);
		}

		@Test
		void anEventWithoutRevisionIsAppliedAsItComes() {
			var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, FROZEN, NOW);
			label.reattach(PEAS, UNDONE);

			label.release(PEAS, Label.UNKNOWN_REVISION);

			assertThat(label.getState()).isEqualTo(LabelState.USED_UP);
		}

		@Test
		void aLabelThatKnowsNoRevisionAcceptsEveryEvent() {
			var label = Label.oneTimeFor(LabelCode.generate(), HOUSEHOLD, PEAS, Label.UNKNOWN_REVISION, NOW);

			label.release(PEAS, CONSUMED);

			assertThat(label.getState()).isEqualTo(LabelState.USED_UP);
		}
	}

}
