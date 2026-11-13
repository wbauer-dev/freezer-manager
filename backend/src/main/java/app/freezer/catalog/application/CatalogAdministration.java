package app.freezer.catalog.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.catalog.ProductId;
import app.freezer.catalog.domain.CatalogRuleViolation;
import app.freezer.catalog.domain.FoodCategories;
import app.freezer.catalog.domain.FoodCategory;
import app.freezer.catalog.domain.FoodCategoryId;
import app.freezer.catalog.domain.HouseholdCatalog;
import app.freezer.catalog.domain.HouseholdCatalogs;
import app.freezer.catalog.domain.Product;
import app.freezer.catalog.domain.Products;
import app.freezer.household.HouseholdMember;

/**
 * Every member can change the household's catalog: categories and products.
 */
@Service
@Transactional
public class CatalogAdministration {

	private final FoodCategories categories;
	private final Products products;
	private final HouseholdCatalogs catalogs;
	private final CatalogSetup setup;

	CatalogAdministration(FoodCategories categories, Products products, HouseholdCatalogs catalogs,
			CatalogSetup setup) {
		this.categories = categories;
		this.products = products;
		this.catalogs = catalogs;
		this.setup = setup;
	}

	/** @param language for the system defaults, if the household's catalog is used for the first time */
	@Transactional(readOnly = true)
	public List<FoodCategory> categories(HouseholdMember member, String language) {
		setup.ensureStarted(member.householdId(), language);
		return categories.findAllOfHousehold(member.householdId());
	}

	/** @param language for the system defaults, if the household's catalog is used for the first time */
	@Transactional(readOnly = true)
	public List<Product> products(HouseholdMember member, String language) {
		setup.ensureStarted(member.householdId(), language);
		return products.findAllOfHousehold(member.householdId());
	}

	@Transactional(readOnly = true)
	public Optional<HouseholdCatalog> catalog(HouseholdMember member) {
		return catalogs.findById(member.householdId());
	}

	/** @param language for the system defaults, if the household's catalog is used for the first time */
	public FoodCategory defineCategory(HouseholdMember member, String name, String emoji, int shelfLifeMonths,
			String language) {
		setup.ensureStarted(member.householdId(), language);
		var category = FoodCategory.define(member.householdId(), name, emoji, shelfLifeMonths,
				categories.nextPosition(member.householdId()));
		requireFreeCategoryName(category);
		return categories.save(category);
	}

	public FoodCategory editCategory(HouseholdMember member, FoodCategoryId id, String name, String emoji,
			int shelfLifeMonths) {
		var category = categoryOf(member, id);
		category.edit(name, emoji, shelfLifeMonths);
		requireFreeCategoryName(category);
		return categories.save(category);
	}

	/** Only categories without products can be removed — a question about the products, so it is asked here. */
	public void removeCategory(HouseholdMember member, FoodCategoryId id) {
		var category = categoryOf(member, id);
		if (products.anyInCategory(id)) {
			throw new CatalogRuleViolation("catalog.category.not-empty", category.getName());
		}
		categories.delete(category);
	}

	public Product defineProduct(HouseholdMember member, String name, FoodCategoryId categoryId) {
		// No need to start the catalog: the category exists, so the catalog has been started.
		var product = Product.define(member.householdId(), name, categoryOf(member, categoryId));
		requireFreeProductName(product);
		return products.save(product);
	}

	public Product editProduct(HouseholdMember member, ProductId id, String name, FoodCategoryId categoryId) {
		var product = productOf(member, id);
		product.rename(name);
		product.moveTo(categoryOf(member, categoryId));
		requireFreeProductName(product);
		return products.save(product);
	}

	/** Items in the freezer keep their name and best-before date. */
	public void removeProduct(HouseholdMember member, ProductId id) {
		products.delete(productOf(member, id));
	}

	// Unique names concern all categories or products of the household: checked here, backed by unique indexes.

	private void requireFreeCategoryName(FoodCategory category) {
		categories.findByName(category.getHouseholdId(), category.getName())
				.filter(other -> !other.getId().equals(category.getId()))
				.ifPresent(other -> {
					throw new CatalogRuleViolation("catalog.category.name-taken", category.getName());
				});
	}

	private void requireFreeProductName(Product product) {
		products.findByName(product.getHouseholdId(), product.getName())
				.filter(other -> !other.getId().equals(product.getId()))
				.ifPresent(other -> {
					throw new CatalogRuleViolation("catalog.product.name-taken", product.getName());
				});
	}

	private FoodCategory categoryOf(HouseholdMember member, FoodCategoryId id) {
		return categories.findById(id)
				.filter(category -> category.belongsTo(member.householdId()))
				.orElseThrow(() -> new CatalogNotFoundException("catalog.category.not-found"));
	}

	private Product productOf(HouseholdMember member, ProductId id) {
		return products.findById(id)
				.filter(product -> product.belongsTo(member.householdId()))
				.orElseThrow(() -> new CatalogNotFoundException("catalog.product.not-found"));
	}

}
