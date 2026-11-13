package app.freezer.inventory;

import java.time.LocalDate;

/**
 * What other modules learn about a frozen item.
 */
public record ItemDescription(FrozenItemId itemId, String productName, LocalDate frozenOn, LocalDate bestBefore) {

}
