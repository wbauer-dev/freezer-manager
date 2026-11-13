package app.freezer.catalog.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.event.types.DomainEvent;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.catalog.ProductId;
import app.freezer.catalog.ProductRenamed;
import app.freezer.household.HouseholdId;

/**
 * A kind of food in a household's catalog, e.g. "Peas" or "Beef goulash". Frozen items are always of a product; its
 * food category determines how long they keep.
 */
@Table(schema = "catalog", name = "product")
public class Product implements AggregateRoot<Product, ProductId> {

	public static final int MAX_NAME_LENGTH = Names.MAX_LENGTH;

	@Identity
	private final ProductId id;
	private final HouseholdId householdId;
	private String name;
	private FoodCategoryId categoryId;
	@Version
	private Long version;

	@Transient
	private final List<DomainEvent> events = new ArrayList<>();

	Product(ProductId id, HouseholdId householdId, String name, FoodCategoryId categoryId, Long version) {
		this.id = id;
		this.householdId = householdId;
		this.name = name;
		this.categoryId = categoryId;
		this.version = version;
	}

	/**
	 * Adds a product to a household's catalog. Names are unique per household; that is checked by the application
	 * service, because it concerns several aggregates.
	 */
	public static Product define(HouseholdId householdId, String name, FoodCategory category) {
		if (!category.belongsTo(householdId)) {
			throw new IllegalArgumentException("catalog.category.unknown");
		}
		return new Product(ProductId.random(), householdId, Names.valid(name, "catalog.product"), category.getId(),
				null);
	}

	/** Renames the product; items in the freezer follow (see {@link ProductRenamed}). */
	public void rename(String newName) {
		var valid = Names.valid(newName, "catalog.product");
		if (!valid.equals(name)) {
			name = valid;
			events.add(new ProductRenamed(householdId, id, valid));
		}
	}

	/** Moves the product to another category. Its new shelf life applies to food frozen from now on. */
	public void moveTo(FoodCategory category) {
		if (!category.belongsTo(householdId)) {
			throw new IllegalArgumentException("catalog.category.unknown");
		}
		categoryId = category.getId();
	}

	public boolean belongsTo(HouseholdId householdId) {
		return this.householdId.equals(householdId);
	}

	@Override
	public ProductId getId() {
		return id;
	}

	public HouseholdId getHouseholdId() {
		return householdId;
	}

	public String getName() {
		return name;
	}

	public FoodCategoryId getCategoryId() {
		return categoryId;
	}

	@DomainEvents
	Collection<DomainEvent> domainEvents() {
		return List.copyOf(events);
	}

	@AfterDomainEventPublication
	void clearDomainEvents() {
		events.clear();
	}

}
