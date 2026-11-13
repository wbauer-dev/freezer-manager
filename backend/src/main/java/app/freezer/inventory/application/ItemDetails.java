package app.freezer.inventory.application;

import app.freezer.inventory.domain.ExpiryStatus;
import app.freezer.inventory.domain.FrozenItem;

/**
 * A frozen item as seen by a member: with the names of its location and whether this member can undo the latest
 * take-out.
 *
 * @param compartmentName {@code null} if the compartment has been removed since the item left the freezer
 */
public record ItemDetails(FrozenItem item, String freezerName, String compartmentName, ExpiryStatus expiryStatus,
		boolean canUndoTakeOut) {

}
