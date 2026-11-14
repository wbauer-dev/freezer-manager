package app.freezer.shopping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.sharedkernel.InvalidValueException;

class ShoppingListTests {

	static final Instant NOW = Instant.parse("2026-11-14T09:00:00Z");
	static final UserId ALICE = UserId.of("alice");
	static final UserId BOB = UserId.of("bob");
	static final ProductId PEAS = ProductId.random();

	ShoppingList list = ShoppingList.startFor(HouseholdId.random());

	@Test
	void addsAProductWithItsNameAndWhoAddedIt() {
		var id = list.addProduct(PEAS, "Peas", " 500 g ", ALICE, "Alice", NOW);

		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.id()).isEqualTo(id);
			assertThat(entry.productId()).isEqualTo(PEAS);
			assertThat(entry.name()).isEqualTo("Peas");
			assertThat(entry.quantity()).isEqualTo("500 g");
			assertThat(entry.source()).isEqualTo(Source.MANUAL);
			assertThat(entry.addedByName()).isEqualTo("Alice");
			assertThat(entry.addedAt()).isEqualTo(NOW);
			assertThat(entry.isOpen()).isTrue();
		});
	}

	@Test
	void addingTheSameProductAgainMergesTheQuantities() {
		var first = list.addProduct(PEAS, "Peas", "500 g", ALICE, "Alice", NOW);

		var second = list.addProduct(PEAS, "Peas", "1 kg", BOB, "Bob", NOW);

		assertThat(second).isEqualTo(first);
		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.quantity()).isEqualTo("500 g + 1 kg");
			assertThat(entry.addedByName()).isEqualTo("Alice");
		});
	}

	@Test
	void aMissingQuantityKeepsTheOtherOne() {
		list.addProduct(PEAS, "Peas", null, ALICE, "Alice", NOW);
		list.addProduct(PEAS, "Peas", "2 bags", ALICE, "Alice", NOW);
		list.addProduct(PEAS, "Peas", " ", ALICE, "Alice", NOW);

		assertThat(list.getEntries()).singleElement().extracting(Entry::quantity).isEqualTo("2 bags");
	}

	@Test
	void freeTextWithTheSameNameIsTheSameThingIgnoringCase() {
		list.addFreeText("Milk", "1 l", ALICE, "Alice", NOW);

		list.addFreeText("  milk ", "2 l", BOB, "Bob", NOW);

		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.productId()).isNull();
			assertThat(entry.name()).isEqualTo("Milk");
			assertThat(entry.quantity()).isEqualTo("1 l + 2 l");
		});
	}

	@Test
	void freeTextAndAProductAreDifferentThingsEvenWithTheSameName() {
		list.addFreeText("Peas", null, ALICE, "Alice", NOW);

		list.addProduct(PEAS, "Peas", null, ALICE, "Alice", NOW);

		assertThat(list.getEntries()).hasSize(2);
	}

	@Test
	void aProductInTheBasketGetsANewEntryWhenAddedAgain() {
		var bought = list.addProduct(PEAS, "Peas", "500 g", ALICE, "Alice", NOW);
		list.check(bought, BOB, "Bob", NOW);

		var again = list.addProduct(PEAS, "Peas", "1 kg", ALICE, "Alice", NOW);

		assertThat(again).isNotEqualTo(bought);
		assertThat(list.getEntries()).extracting(Entry::isOpen).containsExactly(false, true);
	}

	@Test
	void tickingOffRemembersWhoAndWhen() {
		var id = list.addFreeText("Milk", null, ALICE, "Alice", NOW);

		list.check(id, BOB, "Bob", NOW.plusSeconds(60));

		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.isOpen()).isFalse();
			assertThat(entry.checkedBy()).isEqualTo(BOB);
			assertThat(entry.checkedByName()).isEqualTo("Bob");
			assertThat(entry.checkedAt()).isEqualTo(NOW.plusSeconds(60));
		});
	}

	@Test
	void tickingOffTwiceKeepsTheFirstTick() {
		var id = list.addFreeText("Milk", null, ALICE, "Alice", NOW);
		list.check(id, BOB, "Bob", NOW);

		list.check(id, ALICE, "Alice", NOW.plusSeconds(60));

		assertThat(list.getEntries()).singleElement().extracting(Entry::checkedByName).isEqualTo("Bob");
	}

	@Test
	void untickingPutsAnEntryBackOnTheList() {
		var id = list.addFreeText("Milk", "1 l", ALICE, "Alice", NOW);
		list.check(id, BOB, "Bob", NOW);

		list.uncheck(id);
		list.uncheck(id);

		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.isOpen()).isTrue();
			assertThat(entry.checkedBy()).isNull();
			assertThat(entry.checkedByName()).isNull();
			assertThat(entry.quantity()).isEqualTo("1 l");
		});
	}

	@Test
	void untickingMergesIntoAnOpenEntryForTheSameThing() {
		var bought = list.addProduct(PEAS, "Peas", "500 g", ALICE, "Alice", NOW);
		list.check(bought, BOB, "Bob", NOW);
		var open = list.addProduct(PEAS, "Peas", "1 kg", ALICE, "Alice", NOW);

		list.uncheck(bought);

		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.id()).isEqualTo(open);
			assertThat(entry.quantity()).isEqualTo("1 kg + 500 g");
		});
	}

	@Test
	void entriesThatAreGoneAreIgnored() {
		var id = list.addFreeText("Milk", null, ALICE, "Alice", NOW);
		list.remove(id);

		list.check(id, BOB, "Bob", NOW);
		list.uncheck(id);
		list.remove(id);

		assertThat(list.getEntries()).isEmpty();
	}

	@Test
	void doneShoppingClearsTheBasketOnly() {
		var milk = list.addFreeText("Milk", null, ALICE, "Alice", NOW);
		list.addProduct(PEAS, "Peas", null, ALICE, "Alice", NOW);
		list.check(milk, BOB, "Bob", NOW);

		list.clearChecked();

		assertThat(list.getEntries()).extracting(Entry::name).containsExactly("Peas");
	}

	@Test
	void renamedProductsFollowTheCatalog() {
		list.addProduct(PEAS, "Peas", null, ALICE, "Alice", NOW);
		list.addFreeText("Milk", null, ALICE, "Alice", NOW);

		assertThat(list.productRenamed(PEAS, "Garden peas")).isTrue();
		assertThat(list.productRenamed(PEAS, "Garden peas")).isFalse();

		assertThat(list.getEntries()).extracting(Entry::name).containsExactly("Garden peas", "Milk");
	}

	@Test
	void rejectsBlankAndOverlongTexts() {
		assertThatIllegalArgumentException().isThrownBy(() -> list.addFreeText(" ", null, ALICE, "Alice", NOW))
				.withMessage("shopping.entry.name-blank");
		assertThatThrownBy(() -> list.addFreeText("x".repeat(61), null, ALICE, "Alice", NOW))
				.isInstanceOf(InvalidValueException.class)
				.hasMessage("shopping.entry.name-too-long");
		assertThatThrownBy(() -> list.addFreeText("Milk", "x".repeat(61), ALICE, "Alice", NOW))
				.isInstanceOf(InvalidValueException.class)
				.hasMessage("shopping.entry.quantity-too-long");
	}

	@Test
	void aMergedQuantityMustFitToo() {
		list.addFreeText("Milk", "x".repeat(40), ALICE, "Alice", NOW);

		assertThatThrownBy(() -> list.addFreeText("Milk", "y".repeat(20), ALICE, "Alice", NOW))
				.hasMessage("shopping.entry.quantity-too-long");
		assertThat(list.getEntries()).singleElement().extracting(Entry::quantity).isEqualTo("x".repeat(40));
	}

	@Test
	void theListHasALimit() {
		IntStream.range(0, ShoppingList.MAX_ENTRIES)
				.forEach(n -> list.addFreeText("Thing " + n, null, ALICE, "Alice", NOW));

		assertThatThrownBy(() -> list.addFreeText("One more", null, ALICE, "Alice", NOW))
				.isInstanceOf(ShoppingRuleViolation.class)
				.hasMessage("shopping.list.full");
		list.addFreeText("thing 7", "2", ALICE, "Alice", NOW);
	}

}
