package app.freezer.catalog;

import java.time.Period;

/**
 * What other modules learn about a product: its name and how long it keeps in the freezer.
 */
public record ProductDescription(ProductId id, String name, Period shelfLife) {

}
