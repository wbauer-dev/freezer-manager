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
 * <p>
 * Listing categories or products and defining a category expect the household's catalog to be started: call
 * {@link CatalogSetup#ensureStarted} first. Not from within these methods: it starts the catalog in a transaction of
 * its own, which inside one of theirs would hold a second connection — and many members opening their catalogs for
 * the first time at once could exhaust the connection pool.
 * <p>
 * Changes to an existing category or product take the version the member last saw ({@link ExpectedVersion}): if
 * another member changed it in the meantime, the change is rejected instead of silently overwriting theirs.
 */
@Service
@Transactional
public class CatalogAdministration {

	private final FoodCategories categories;
	private final Products products;
	private final HouseholdCatalogs catalogs;

	CatalogAdministration(FoodCategories categories, Products products, HouseholdCatalogs catalogs) {
		this.categories = categories;
		this.products = products;
		this.catalogs = catalogs;
	}

	/** Expects the catalog to be started ({@link CatalogSetup#ensureStarted}). */
	@Transactional(readOnly = true)
	public List<FoodCategory> categories(HouseholdMember member) {
		return categories.findAllOfHousehold(member.householdId());
	}

	/** Expects the catalog to be started ({@link CatalogSetup#ensureStarted}). */
	@Transactional(readOnly = true)
	public List<Product> products(HouseholdMember member) {
		return products.findAllOfHousehold(member.householdId());
	}

	@Transactional(readOnly = true)
	public Optional<HouseholdCatalog> catalog(HouseholdMember member) {
		return catalogs.findById(member.householdId());
	}

	/** Expects the catalog to be started ({@link CatalogSetup#ensureStarted}). */
	public FoodCategory defineCategory(HouseholdMember member, String name, String emoji, int shelfLifeMonths) {
		var category = FoodCategory.define(member.householdId(), name, emoji, shelfLifeMonths,
				categories.nextPosition(member.householdId()));
		requireFreeCategoryName(category);
		return categories.save(category);
	}

	public FoodCategory editCategory(HouseholdMember member, FoodCategoryId id, ExpectedVersion version, String name,
			String emoji, int shelfLifeMonths) {
		var category = categoryOf(member, id, version);
		category.edit(name, emoji, shelfLifeMonths);
		requireFreeCategoryName(category);
		return categories.save(category);
	}

	/** Only categories without products can be removed — a question about the products, so it is asked here. */
	public void removeCategory(HouseholdMember member, FoodCategoryId id, ExpectedVersion version) {
		var category = categoryOf(member, id, version);
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

	public Product editProduct(HouseholdMember member, ProductId id, ExpectedVersion version, String name,
			FoodCategoryId categoryId) {
		var product = productOf(member, id, version);
		product.rename(name);
		product.moveTo(categoryOf(member, categoryId));
		requireFreeProductName(product);
		return products.save(product);
	}

	/** Items in the freezer keep their name and best-before date. */
	public void removeProduct(HouseholdMember member, ProductId id, ExpectedVersion version) {
		products.delete(productOf(member, id, version));
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

	/** The category to change, as the member saw it. */
	private FoodCategory categoryOf(HouseholdMember member, FoodCategoryId id, ExpectedVersion version) {
		var category = categoryOf(member, id);
		version.requireCurrent(category.getVersion(), "catalog.category.changed");
		return category;
	}

	private Product productOf(HouseholdMember member, ProductId id, ExpectedVersion version) {
		var product = products.findById(id)
				.filter(candidate -> candidate.belongsTo(member.householdId()))
				.orElseThrow(() -> new CatalogNotFoundException("catalog.product.not-found"));
		version.requireCurrent(product.getVersion(), "catalog.product.changed");
		return product;
	}

}
