package app.freezer.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

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

	final Freezer kitchen = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top", "Bottom"));
	final CompartmentId top = kitchen.getCompartments().getFirst().id();

	FrozenItem freezeMincedMeat(int portions) {
		var item = FrozenItem.freeze(HOUSEHOLD, kitchen.locationOf(top), MINCED_MEAT, "Minced meat", LABEL,
				Portions.of(portions), 250, FROZEN_ON, BestBefore.calculate(FROZEN_ON, Period.ofMonths(3)),
				"for Bolognese", ALICE);
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
			assertThat(item.canUndoTakeOut(ALICE, nineMinutesLater, kitchen, false)).isTrue();
			item.undoTakeOut(ALICE, nineMinutesLater, kitchen, false);

			assertThat(item.getPortions()).isEqualTo(Portions.of(3));
			assertThat(item.getWithdrawals()).isEmpty();
			assertThat(item.domainEvents())
					.containsExactly(new TakeOutUndone(item.getId(), HOUSEHOLD, MINCED_MEAT, 2, 3, LABEL));
		}

		@Test
		void bringsAConsumedItemBackIntoTheFreezer() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);

			item.undoTakeOut(ALICE, NOW.plusSeconds(30), kitchen, false);

			assertThat(item.isInFreezer()).isTrue();
			assertThat(item.getRemovedAt()).isEmpty();
		}

		@Test
		void bringsAConsumedItemBackOnlyIfItsCompartmentStillExists() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);
			kitchen.removeCompartment(top);

			assertThat(item.canUndoTakeOut(ALICE, NOW, kitchen, false)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW, kitchen, false))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.undo-place-removed");
			assertThat(item.getState()).isEqualTo(ItemState.CONSUMED);
		}

		@Test
		void bringsAConsumedItemBackOnlyIfItsFreezerIsStillInUse() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);
			kitchen.remove(NOW);

			assertThat(item.canUndoTakeOut(ALICE, NOW, kitchen, false)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW, kitchen, false))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.undo-place-removed");
		}

		@Test
		void bringsAConsumedItemBackOnlyIntoItsOwnFreezer() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);
			var otherFreezer = Freezer.setUp(HOUSEHOLD, "Cellar", List.of("Top"));

			assertThat(item.canUndoTakeOut(ALICE, NOW, otherFreezer, false)).isFalse();
		}

		@Test
		void bringsAConsumedItemBackOnlyIfItsLabelIsNotOnAnotherItemByNow() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);

			assertThat(item.canUndoTakeOut(ALICE, NOW, kitchen, true)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW, kitchen, true))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.label.in-use");
		}

		@Test
		void isNotPossibleAfterTenMinutes() {
			var item = freezeMincedMeat(3);
			item.takeOut(1, ALICE, "Alice", NOW);
			var elevenMinutesLater = NOW.plus(Duration.ofMinutes(11));

			assertThat(item.canUndoTakeOut(ALICE, elevenMinutesLater, kitchen, false)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, elevenMinutesLater, kitchen, false))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.undo-expired");
		}

		@Test
		void isOnlyPossibleForTheMemberWhoTookThePortionsOut() {
			var item = freezeMincedMeat(3);
			item.takeOut(1, ALICE, "Alice", NOW);

			assertThat(item.canUndoTakeOut(BOB, NOW, kitchen, false)).isFalse();
			assertThatThrownBy(() -> item.undoTakeOut(BOB, NOW, kitchen, false))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.undo-by-other");
		}

		@Test
		void needsATakeOut() {
			var item = freezeMincedMeat(3);

			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW, kitchen, false))
					.isInstanceOf(InventoryRuleViolation.class)
					.hasMessage("inventory.take-out.nothing-to-undo");
		}
	}

	@Nested
	class FollowingARenamedProduct {

		@Test
		void takesTheNewNameWhileInTheFreezer() {
			var item = freezeMincedMeat(2);

			assertThat(item.productRenamed("Ground beef")).isTrue();
			assertThat(item.productRenamed("Ground beef")).isFalse();
			assertThat(item.getProductName()).isEqualTo("Ground beef");
		}

		@Test
		void keepsTheNameItLeftTheFreezerWith() {
			var item = freezeMincedMeat(1);
			item.takeOut(1, ALICE, "Alice", NOW);

			assertThat(item.productRenamed("Ground beef")).isFalse();
			assertThat(item.getProductName()).isEqualTo("Minced meat");
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
			assertThatThrownBy(() -> item.undoTakeOut(ALICE, NOW, kitchen, false))
					.isInstanceOf(InventoryRuleViolation.class);
			assertThatThrownBy(() -> item.discard(DiscardReason.OTHER, NOW)).isInstanceOf(InventoryRuleViolation.class);
		}
	}

}
