package app.freezer.catalog.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import app.freezer.catalog.domain.FoodCategories;
import app.freezer.catalog.domain.FoodCategory;
import app.freezer.catalog.domain.HouseholdCatalog;
import app.freezer.catalog.domain.HouseholdCatalogs;
import app.freezer.catalog.domain.Product;
import app.freezer.catalog.domain.Products;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;

/**
 * Starting and resetting a household's catalog.
 */
@Service
public class CatalogSetup {

	private final HouseholdCatalogs catalogs;
	private final FoodCategories categories;
	private final Products products;
	private final CatalogTemplates templates;
	private final Clock clock;
	private final TransactionTemplate newTransaction;

	CatalogSetup(HouseholdCatalogs catalogs, FoodCategories categories, Products products, CatalogTemplates templates,
			Clock clock, PlatformTransactionManager transactions) {
		this.catalogs = catalogs;
		this.categories = categories;
		this.products = products;
		this.templates = templates;
		this.clock = clock;
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * A household's catalog starts with the system defaults in the language of the member who first uses it. Pages
	 * load categories and products in parallel, so two requests may try at once; the second one finds the household's
	 * catalog started by the first and continues.
	 */
	public void ensureStarted(HouseholdId householdId, String language) {
		if (catalogs.existsById(householdId)) {
			return;
		}
		try {
			newTransaction.executeWithoutResult(status -> {
				var template = templates.inLanguage(language);
				catalogs.save(HouseholdCatalog.start(householdId, template.language(), Instant.now(clock)));
				addDefaults(householdId, template);
			});
		}
		catch (RuntimeException e) {
			if (!isDuplicateKey(e)) {
				throw e;
			}
		}
	}

	/**
	 * Removes all categories and products of the household and starts over — empty, or with the system defaults in the
	 * given language. Items in the freezer keep their names and best-before dates.
	 */
	@Transactional
	public void reset(HouseholdMember member, boolean withDefaults, String language) {
		var householdId = member.householdId();
		var now = Instant.now(clock);
		products.deleteAllOfHousehold(householdId);
		categories.deleteAllOfHousehold(householdId);
		var template = withDefaults ? templates.inLanguage(language) : null;
		if (template != null) {
			addDefaults(householdId, template);
		}
		var startedWith = template == null ? null : template.language();
		var catalog = catalogs.findById(householdId).orElse(null);
		if (catalog == null) {
			catalog = HouseholdCatalog.start(householdId, startedWith, now);
		}
		else {
			catalog.restart(startedWith, now);
		}
		catalogs.save(catalog);
	}

	private void addDefaults(HouseholdId householdId, CatalogTemplate template) {
		int position = 1;
		for (var defaults : template.categories()) {
			var category = categories.save(FoodCategory.define(householdId, defaults.name(), defaults.emoji(),
					defaults.shelfLifeMonths(), position++));
			defaults.products().forEach(name -> products.save(Product.define(householdId, name, category)));
		}
	}

	private static boolean isDuplicateKey(Throwable e) {
		for (var cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof DuplicateKeyException) {
				return true;
			}
		}
		return false;
	}

}
