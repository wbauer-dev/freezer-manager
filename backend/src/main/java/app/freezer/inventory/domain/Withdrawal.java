package app.freezer.inventory.domain;

import java.time.Instant;

import org.jmolecules.ddd.types.ValueObject;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.UserId;

/**
 * Portions taken out of a frozen item by a member. The member's name is a snapshot for the item's history.
 */
@Table(schema = "inventory", name = "withdrawal")
public record Withdrawal(int portions, UserId takenBy, String takenByName, Instant takenAt) implements ValueObject {

}
