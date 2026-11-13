package app.freezer.catalog.domain;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a food category of a household's catalog.
 */
public record FoodCategoryId(UUID id) implements Identifier {

	public FoodCategoryId {
		if (id == null) {
			throw new IllegalArgumentException("catalog.category.unknown");
		}
	}

	public static FoodCategoryId of(UUID id) {
		return new FoodCategoryId(id);
	}

	public static FoodCategoryId random() {
		return new FoodCategoryId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
