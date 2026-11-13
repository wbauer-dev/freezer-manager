package app.freezer.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Period;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import app.freezer.catalog.ProductRenamed;
import app.freezer.household.HouseholdId;

class CatalogDomainTests {

	static final HouseholdId HOUSEHOLD = HouseholdId.of(UUID.randomUUID());

	FoodCategory vegetables = FoodCategory.define(HOUSEHOLD, " Vegetables ", "🥦", 12, 1);

	@Test
	void categoryDefinesTheShelfLifeOfItsFood() {
		assertThat(vegetables.getName()).isEqualTo("Vegetables");
		assertThat(vegetables.shelfLife()).isEqualTo(Period.ofMonths(12));

		vegetables.edit("Veggies", "🥕", 10);

		assertThat(vegetables.getName()).isEqualTo("Veggies");
		assertThat(vegetables.getEmoji()).isEqualTo("🥕");
		assertThat(vegetables.shelfLife()).isEqualTo(Period.ofMonths(10));
	}

	@Test
	void categoryNeedsANameAnEmojiAndAPlausibleShelfLife() {
		assertThatIllegalArgumentException().isThrownBy(() -> FoodCategory.define(HOUSEHOLD, " ", "🥦", 12, 1))
				.withMessage("catalog.category.name-blank");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> FoodCategory.define(HOUSEHOLD, "x".repeat(41), "🥦", 12, 1))
				.withMessage("catalog.category.name-too-long");
		assertThatIllegalArgumentException().isThrownBy(() -> vegetables.edit("Vegetables", "", 12))
				.withMessage("catalog.category.emoji-invalid");
		assertThatIllegalArgumentException().isThrownBy(() -> vegetables.edit("Vegetables", "🥦", 0))
				.withMessage("catalog.category.shelf-life-invalid");
		assertThatIllegalArgumentException().isThrownBy(() -> vegetables.edit("Vegetables", "🥦", 61))
				.withMessage("catalog.category.shelf-life-invalid");
	}

	@Test
	void renamedProductTellsTheOtherModules() {
		var peas = Product.define(HOUSEHOLD, "Peas", vegetables);

		peas.rename("Peas");
		assertThat(peas.domainEvents()).isEmpty();

		peas.rename("Garden peas ");
		assertThat(peas.getName()).isEqualTo("Garden peas");
		assertThat(peas.domainEvents()).containsExactly(new ProductRenamed(HOUSEHOLD, peas.getId(), "Garden peas"));
	}

	@Test
	void productsOnlyBelongToCategoriesOfTheirHousehold() {
		var elsewhere = FoodCategory.define(HouseholdId.of(UUID.randomUUID()), "Fruit", "🍓", 12, 1);
		var peas = Product.define(HOUSEHOLD, "Peas", vegetables);

		assertThatIllegalArgumentException().isThrownBy(() -> Product.define(HOUSEHOLD, "Mango", elsewhere));
		assertThatIllegalArgumentException().isThrownBy(() -> peas.moveTo(elsewhere));
	}

	@Test
	void catalogRemembersHowItStarted() {
		var started = java.time.Instant.parse("2026-11-01T10:00:00Z");
		var catalog = HouseholdCatalog.start(HOUSEHOLD, "de", started);
		assertThat(catalog.getLanguage()).contains("de");

		catalog.restart(null, started.plusSeconds(60));

		assertThat(catalog.getLanguage()).isEmpty();
		assertThat(catalog.getStartedAt()).isEqualTo(started.plusSeconds(60));
	}

}
