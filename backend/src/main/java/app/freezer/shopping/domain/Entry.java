package app.freezer.shopping.domain;

import java.time.Instant;
import java.util.Locale;

import org.jmolecules.ddd.types.Entity;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.catalog.ProductId;
import app.freezer.household.UserId;

/**
 * One thing to buy. Part of the {@link ShoppingList} aggregate.
 * <p>
 * Either a product of the household's catalog ({@code productId} set, {@code name} a snapshot of its name) or free text
 * ({@code productId} null). The quantity is a free note such as "500 g". An entry is <em>open</em> until a member ticks
 * it off; then {@code checkedBy}, {@code checkedByName} and {@code checkedAt} are set. Member names are snapshots.
 */
@Table(schema = "shopping", name = "entry")
public record Entry(EntryId id, ProductId productId, String name, String quantity, Source source, UserId addedBy,
		String addedByName, Instant addedAt, UserId checkedBy, String checkedByName, Instant checkedAt)
		implements Entity<ShoppingList, EntryId> {

	@Override
	public EntryId getId() {
		return id;
	}

	public boolean isOpen() {
		return checkedAt == null;
	}

	/** The same product, or — for free text — the same name, ignoring case. */
	boolean isFor(ProductId otherProduct, String otherName) {
		if (productId != null || otherProduct != null) {
			return productId != null && productId.equals(otherProduct);
		}
		return name.toLowerCase(Locale.ROOT).equals(otherName.toLowerCase(Locale.ROOT));
	}

	Entry withQuantity(String newQuantity) {
		return new Entry(id, productId, name, newQuantity, source, addedBy, addedByName, addedAt, checkedBy,
				checkedByName, checkedAt);
	}

	Entry withName(String newName) {
		return new Entry(id, productId, newName, quantity, source, addedBy, addedByName, addedAt, checkedBy,
				checkedByName, checkedAt);
	}

	Entry checked(UserId by, String byName, Instant at) {
		return new Entry(id, productId, name, quantity, source, addedBy, addedByName, addedAt, by, byName, at);
	}

	Entry unchecked() {
		return new Entry(id, productId, name, quantity, source, addedBy, addedByName, addedAt, null, null, null);
	}

}
