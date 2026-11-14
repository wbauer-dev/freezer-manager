package app.freezer.shopping.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.sharedkernel.InvalidValueException;

/**
 * The household's shared shopping list.
 * <p>
 * There is at most one <em>open</em> entry for the same thing: adding a product (or the same free text) again adds
 * its quantity note to the open entry instead of listing it twice. Ticked-off entries stay on the list, in the basket,
 * until the shopping is done.
 * <p>
 * Ticking off, unticking and removing are idempotent, and an entry that is gone is simply ignored: another member may
 * have removed it a moment earlier. That is what allows a change that collided with another member's change to be
 * applied again to the fresh list.
 */
@Table(schema = "shopping", name = "shopping_list")
public class ShoppingList implements AggregateRoot<ShoppingList, HouseholdId> {

	public static final int MAX_NAME_LENGTH = 60;
	public static final int MAX_QUANTITY_LENGTH = 60;
	/** The whole list is one aggregate, loaded and saved as a whole; the limit keeps it small. */
	public static final int MAX_ENTRIES = 200;

	private static final String QUANTITY_SEPARATOR = " + ";

	@Identity
	private final HouseholdId householdId;
	@MappedCollection(idColumn = "household_id", keyColumn = "position")
	private final List<Entry> entries;
	@Version
	private Long version;

	ShoppingList(HouseholdId householdId, List<Entry> entries, Long version) {
		this.householdId = householdId;
		this.entries = new ArrayList<>(entries);
		this.version = version;
	}

	public static ShoppingList startFor(HouseholdId householdId) {
		return new ShoppingList(householdId, List.of(), null);
	}

	/**
	 * Adds a product of the household's catalog, or adds the quantity to its open entry.
	 *
	 * @param productName the product's current name in the catalog
	 * @return the entry that now lists the product
	 */
	public EntryId addProduct(ProductId productId, String productName, String quantity, UserId by, String byName,
			Instant now) {
		Objects.requireNonNull(productId, "productId");
		return add(productId, productName, quantity, by, byName, now);
	}

	/**
	 * Adds something that is not in the catalog, e.g. "Milk", or adds the quantity to the open entry with the same
	 * name.
	 *
	 * @return the entry that now lists it
	 */
	public EntryId addFreeText(String name, String quantity, UserId by, String byName, Instant now) {
		return add(null, name, quantity, by, byName, now);
	}

	/** Ticks an entry off: it is in the basket. */
	public void check(EntryId entryId, UserId by, String byName, Instant now) {
		find(entryId).filter(Entry::isOpen).ifPresent(entry -> replace(entry, entry.checked(by, byName, now)));
	}

	/**
	 * Puts a ticked-off entry back on the list. If the same thing was added again in the meantime, the two entries
	 * become one.
	 */
	public void uncheck(EntryId entryId) {
		var entry = find(entryId).filter(checked -> !checked.isOpen());
		if (entry.isEmpty()) {
			return;
		}
		var open = openEntryFor(entry.get().productId(), entry.get().name());
		if (open.isPresent()) {
			replace(open.get(), open.get().withQuantity(merged(open.get().quantity(), entry.get().quantity())));
			entries.remove(entry.get());
		}
		else {
			replace(entry.get(), entry.get().unchecked());
		}
	}

	public void remove(EntryId entryId) {
		entries.removeIf(entry -> entry.id().equals(entryId));
	}

	/** The shopping is done: everything in the basket leaves the list. */
	public void clearChecked() {
		entries.removeIf(entry -> !entry.isOpen());
	}

	/**
	 * A product was renamed in the catalog; its entries follow.
	 *
	 * @return whether an entry changed
	 */
	public boolean productRenamed(ProductId productId, String name) {
		var changed = false;
		for (int index = 0; index < entries.size(); index++) {
			var entry = entries.get(index);
			if (productId.equals(entry.productId()) && !entry.name().equals(name)) {
				entries.set(index, entry.withName(name));
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public HouseholdId getId() {
		return householdId;
	}

	/** All entries in the order they were added; open and ticked-off ones alike. */
	public List<Entry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	private EntryId add(ProductId productId, String name, String quantity, UserId by, String byName, Instant now) {
		var validName = validName(name);
		var validQuantity = validQuantity(quantity);
		var open = openEntryFor(productId, validName);
		if (open.isPresent()) {
			replace(open.get(), open.get().withQuantity(merged(open.get().quantity(), validQuantity)));
			return open.get().id();
		}
		if (entries.size() >= MAX_ENTRIES) {
			throw new ShoppingRuleViolation("shopping.list.full", MAX_ENTRIES);
		}
		var entry = new Entry(EntryId.random(), productId, validName, validQuantity, Source.MANUAL, by, byName, now,
				null, null, null);
		entries.add(entry);
		return entry.id();
	}

	private Optional<Entry> find(EntryId entryId) {
		return entries.stream().filter(entry -> entry.id().equals(entryId)).findFirst();
	}

	private Optional<Entry> openEntryFor(ProductId productId, String name) {
		return entries.stream().filter(Entry::isOpen).filter(entry -> entry.isFor(productId, name)).findFirst();
	}

	private void replace(Entry entry, Entry replacement) {
		entries.set(entries.indexOf(entry), replacement);
	}

	/** "500 g" and "1 kg" become "500 g + 1 kg"; a missing note keeps the other one. */
	private static String merged(String quantity, String added) {
		if (quantity == null) {
			return added;
		}
		if (added == null) {
			return quantity;
		}
		return validQuantity(quantity + QUANTITY_SEPARATOR + added);
	}

	private static String validName(String name) {
		var trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("shopping.entry.name-blank");
		}
		if (trimmed.length() > MAX_NAME_LENGTH) {
			throw new InvalidValueException("shopping.entry.name-too-long", MAX_NAME_LENGTH);
		}
		return trimmed;
	}

	private static String validQuantity(String quantity) {
		if (quantity == null || quantity.isBlank()) {
			return null;
		}
		var trimmed = quantity.strip();
		if (trimmed.length() > MAX_QUANTITY_LENGTH) {
			throw new InvalidValueException("shopping.entry.quantity-too-long", MAX_QUANTITY_LENGTH);
		}
		return trimmed;
	}

}
