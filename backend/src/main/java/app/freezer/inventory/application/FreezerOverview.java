package app.freezer.inventory.application;

import java.util.List;

import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.FreezerId;

/**
 * A freezer with the number of items in each compartment.
 */
public record FreezerOverview(FreezerId id, String name, List<CompartmentOverview> compartments) {

	/** A compartment with the number of items in it. */
	public record CompartmentOverview(CompartmentId id, String name, int itemCount) {
	}

}
