package app.freezer.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.inventory.DiscardReason;
import app.freezer.inventory.ItemConsumed;
import app.freezer.inventory.ItemDiscarded;
import app.freezer.inventory.ItemFrozen;
import app.freezer.inventory.PortionsTakenOut;
import app.freezer.inventory.TakeOutUndone;
import app.freezer.media.PhotoId;
import app.freezer.sharedkernel.LabelCode;

class FrozenItemTests {

	static final HouseholdId HOUSEHOLD = HouseholdId.random();
	static final ProductId MINCED_MEAT = ProductId.random();
	static final UserId ALICE = UserId.of("alice");
	static final UserId BOB = UserId.of("bob");
	static final LocalDate FROZEN_ON = LocalDate.of(2026, 11, 1);
	static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
	static final LabelCode LABEL = LabelCode.of("K7Q2M9XA");

	static FrozenItem freezeMincedMeat(int portions) {
		var item = FrozenItem.freeze(HOUSEHOLD, new Location(FreezerId.random(), CompartmentId.random()), MINCED_MEAT,
				"Minced meat", LABEL, Portions.of(portions), 250, FROZEN_ON,
				BestBefore.calculate(FROZEN_ON, Period.ofMonths(3)), "for Bolognese", ALICE);
		item.clearDomainEvents();
		return item;
	}

	@Nested
	class Freezing {

		@Test
		void announcesTheNewItem() {
			var item = FrozenItem.freeze(HOUSEHOLD, new Location(FreezerId.random(), CompartmentId.random()),
					MINCED_MEAT, "Minced meat", LABEL, Portions.of(4), null, FROZEN_ON,
					BestBefore.calculate(FROZEN_ON, Period.ofMonths(3)), null, ALICE);

			assertThat(item.isInFreezer()).isTrue();
			assertThat(item.domainEvents()).containsExactly(
					new ItemFrozen(item.getId(), HOUSEHOLD, MINCED_MEAT, "Minced meat", 4, FROZEN_ON,
							LocalDate.of(2027, 2, 1), LABEL));
		}

		@Test
		void rejectsBestBeforeDateBeforeFreezingDate() {
			assertThatIllegalArgumentException().isThrownBy(() -> FrozenItem.freeze(HOUSEHOLD,
					new Location(FreezerId.random(), CompartmentId.random()), MINCED_MEAT, "Minced meat", LABEL,
					Portions.of(1), null, FROZEN_ON, BestBefore.overriddenWith(FROZEN_ON.minusDays(1)), null, ALICE));
		}

		@Test
		void blankNoteIsNoNote() {
			var item = FrozenItem.freeze(HOUSEHOLD, new Location(FreezerId.random(), CompartmentId.random()),
					MINCED_MEAT, "Minced meat", LABEL, Portions.of(1), null, FROZEN_ON,
					BestBefore.calculate(FROZEN_ON, Period.ofMonths(3)), "   ", ALICE);

			assertThat(item.getNote()).isEmpty();
		}
	}

	@Nested
	class TakingOut {

		@Test
		void reducesRemainingPortions() {
			var item = freezeMincedMeat(4);

			item.takeOut(1, ALICE, "Alice", NOW);

			assertThat(item.getPortions()).isEqualTo(new Portions(4, 3));
			assertThat(item.isInFreezer()).isTrue();
			assertThat(item.getWithdrawals()).containsExactly(new Withdrawal(1, ALICE, "Alice", NOW));
			assertThat(item.domainEvents())
					.containsExactly(new PortionsTakenOut(item.getId(), HOUSEHOLD, MINCED_MEAT, 1, 3, LABEL));
		}

		@Test
		void lastPortionConsumesTheItem() {
			var item = freezeMincedMeat(2);

			item.takeOut(2, ALICE, "Alice", NOW);

			assertThat(item.getState()).isEqualTo(ItemState.CONSUMED);
			assertThat(item.getRemovedAt()).contains(NOW);
			assertThat(item.domainEvents()).last()
					.isEqualTo(new ItemConsumed(item.getId(), HOUSEHOLD, MINCED_MEAT, LABEL));
		}

		@Test
		void neverMoreThanIsLeft() {
			var item = freezeMincedMeat(2);

			assertThatThrownBy(() -> item.takeOut(3, ALICE, "Alice", NOW))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.too-many");
		}

		@Test
		void notFromAConsumedItem() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);

			assertThatThrownBy(() -> item.takeOut(1, ALICE, "Alice", NOW))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.item.not-in-freezer");
		}
	}

	@Nested
	class UndoingATakeOut {

		@Test
		void putsThePortionsBackWithinTenMinutes() {
			var item = freezeMincedMeat(3);
			item.takeOut(2, ALICE, "Alice", NOW);
			item.clearDomainEvents();

			var nineMinutesLater = NOW.plus(Duration.ofMinutes(9));
			assertThat(item.canUndoTakeOut(ALICE, nineMinutesLater)).isTrue();
			item.undoTakeOut(ALICE, nineMinutesLater);

			assertThat(item.getPortions()).isEqualTo(Portions.of(3));
			assertThat(item.getWithdrawals()).isEmpty();
			assertThat(item.domainEvents())
					.containsExactly(new TakeOutUndone(item.getId(), HOUSEHOLD, MINCED_MEAT, 2, 3, LABEL));
		}

		@Test
		void bringsAConsumedItemBackIntoTheFreezer() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);

			item.undoTakeOut(ALICE, NOW.plusSeconds(30));

			assertThat(item.isInFreezer()).isTrue();
			assertThat(item.getRemovedAt()).isEmpty();
		}

		@Test
		void isNotPossibleAfterTenMinutes() {
			var item = freezeMincedMeat(3);
			item.takeOut(1, ALICE, "Alice", NOW);
			var elevenMinutesLater = NOW.plus(Duration.ofMinutes(11));

			assertThat(item.canUndoTakeOut(ALICE, elevenMinutesLater)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, elevenMinutesLater))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.undo-expired");
		}

		@Test
		void isOnlyPossibleForTheMemberWhoTookThePortionsOut() {
			var item = freezeMincedMeat(3);
			item.takeOut(1, ALICE, "Alice", NOW);

			assertThat(item.canUndoTakeOut(BOB, NOW)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(BOB, NOW))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.undo-by-other");
		}

		@Test
		void needsATakeOut() {
			var item = freezeMincedMeat(3);

			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.nothing-to-undo");
		}
	}

	@Nested
	class Discarding {

		@Test
		void removesTheItemWithAReason() {
			var item = freezeMincedMeat(2);

			item.discard(DiscardReason.FREEZER_BURN, NOW);

			assertThat(item.getState()).isEqualTo(ItemState.DISCARDED);
			assertThat(item.getDiscardReason()).contains(DiscardReason.FREEZER_BURN);
			assertThat(item.domainEvents())
					.containsExactly(
							new ItemDiscarded(item.getId(), HOUSEHOLD, MINCED_MEAT, DiscardReason.FREEZER_BURN, LABEL));
		}

		@Test
		void allowsNoNewPhoto() {
			var item = freezeMincedMeat(2);
			item.discard(DiscardReason.EXPIRED, NOW);

			assertThatThrownBy(() -> item.attachPhoto(PhotoId.random())).isInstanceOf(InventoryRuleViolation.class);
		}

		@Test
		void makesTheItemUnchangeable() {
			var item = freezeMincedMeat(2);
			item.takeOut(1, ALICE, "Alice", NOW);
			item.discard(DiscardReason.EXPIRED, NOW);

			assertThatThrownBy(() -> item.takeOut(1, ALICE, "Alice", NOW)).isInstanceOf(InventoryRuleViolation.class);
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW)).isInstanceOf(InventoryRuleViolation.class);
			assertThatThrownBy(() -> item.discard(DiscardReason.OTHER, NOW)).isInstanceOf(InventoryRuleViolation.class);
		}
	}

}
