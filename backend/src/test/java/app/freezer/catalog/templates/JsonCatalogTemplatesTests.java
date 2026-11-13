package app.freezer.catalog.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import app.freezer.catalog.application.CatalogTemplate;
import app.freezer.catalog.domain.FoodCategory;
import app.freezer.catalog.domain.Product;
import app.freezer.household.HouseholdId;

/**
 * Guards the system defaults: a new language file must contain the same categories as the English one, and all of it
 * must be valid catalog content.
 */
class JsonCatalogTemplatesTests {

	JsonCatalogTemplates templates = new JsonCatalogTemplates();

	@Test
	void offersEnglishAndGerman() {
		assertThat(templates.all()).extracting(CatalogTemplate::language).containsExactly("de", "en");
		assertThat(templates.inLanguage("de").categories().getFirst().name()).isEqualTo("Rind & Lamm");
	}

	@Test
	void fallsBackToEnglish() {
		assertThat(templates.inLanguage("fr").language()).isEqualTo("en");
	}

	@Test
	void everyLanguageHasTheSameCategoriesWithTheSameShelfLife() {
		var english = templates.inLanguage("en").categories();
		for (var template : templates.all()) {
			assertThat(template.categories())
					.as(template.language())
					.extracting(CatalogTemplate.Category::key, CatalogTemplate.Category::emoji,
							CatalogTemplate.Category::shelfLifeMonths)
					.containsExactlyElementsOf(english.stream()
							.map(category -> org.assertj.core.groups.Tuple.tuple(category.key(), category.emoji(),
									category.shelfLifeMonths()))
							.toList());
		}
	}

	@Test
	void templatesAreValidCatalogs() {
		var household = HouseholdId.of(UUID.randomUUID());
		for (var template : templates.all()) {
			var categoryNames = new HashSet<String>();
			var productNames = new HashSet<String>();
			for (var defaults : template.categories()) {
				var category = FoodCategory.define(household, defaults.name(), defaults.emoji(),
						defaults.shelfLifeMonths(), 1);
				assertThat(categoryNames.add(category.getName().toLowerCase(Locale.ROOT))).as(defaults.name()).isTrue();
				for (var name : defaults.products()) {
					var product = Product.define(household, name, category);
					assertThat(productNames.add(product.getName().toLowerCase(Locale.ROOT))).as(name).isTrue();
				}
			}
		}
	}

}
