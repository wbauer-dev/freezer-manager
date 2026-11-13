package app.freezer.catalog;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a product of a household's catalog.
 */
public record ProductId(UUID id) implements Identifier {

	public ProductId {
		if (id == null) {
			throw new IllegalArgumentException("Product id must not be null");
		}
	}

	public static ProductId of(UUID id) {
		return new ProductId(id);
	}

	public static ProductId random() {
		return new ProductId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
