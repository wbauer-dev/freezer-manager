package app.freezer.inventory.domain;

import org.jmolecules.ddd.types.Entity;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A drawer or shelf of a freezer. Part of the {@link Freezer} aggregate.
 */
@Table(schema = "inventory", name = "compartment")
public record Compartment(CompartmentId id, String name) implements Entity<Freezer, CompartmentId> {

	@Override
	public CompartmentId getId() {
		return id;
	}

}
