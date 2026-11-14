package app.freezer.inventory.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.InvalidValueException;

/**
 * A physical freezer of a household with its compartments.
 * <p>
 * Rules: a freezer has at least one and at most {@link #MAX_COMPARTMENTS} compartments, with unique names. Whether a
 * compartment or the whole freezer is empty is not known here — that is checked by the application service before
 * removing one. A removed freezer is kept for the history of the items that were stored in it, but cannot be changed
 * or used any more.
 */
@Table(schema = "inventory", name = "freezer")
public class Freezer implements AggregateRoot<Freezer, FreezerId> {

	public static final int MAX_NAME_LENGTH = 40;
	public static final int MAX_COMPARTMENTS = 20;

	@Identity
	private final FreezerId id;
	private final HouseholdId householdId;
	private final String name;
	@MappedCollection(idColumn = "freezer_id", keyColumn = "position")
	private final List<Compartment> compartments;
	private Instant removedAt;
	@Version
	private Long version;

	Freezer(FreezerId id, HouseholdId householdId, String name, List<Compartment> compartments, Instant removedAt,
			Long version) {
		this.id = id;
		this.householdId = householdId;
		this.name = name;
		this.compartments = new ArrayList<>(compartments);
		this.removedAt = removedAt;
		this.version = version;
	}

	public static Freezer setUp(HouseholdId householdId, String name, List<String> compartmentNames) {
		if (compartmentNames == null || compartmentNames.isEmpty()) {
			throw new IllegalArgumentException("inventory.freezer.compartment-required");
		}
		var freezer = new Freezer(FreezerId.random(), householdId, validName(name, "inventory.freezer"), List.of(),
				null, null);
		compartmentNames.forEach(freezer::addCompartment);
		return freezer;
	}

	public Compartment addCompartment(String name) {
		requireNotRemoved();
		if (compartments.size() >= MAX_COMPARTMENTS) {
			throw new InventoryRuleViolation("inventory.freezer.too-many-compartments", MAX_COMPARTMENTS);
		}
		var validName = validName(name, "inventory.compartment");
		if (compartments.stream().anyMatch(compartment -> sameName(compartment.name(), validName))) {
			throw new InventoryRuleViolation("inventory.compartment.name-taken", validName);
		}
		var compartment = new Compartment(CompartmentId.random(), validName);
		compartments.add(compartment);
		return compartment;
	}

	/**
	 * Removes a compartment. The caller must make sure it is empty.
	 */
	public void removeCompartment(CompartmentId compartmentId) {
		requireNotRemoved();
		var compartment = compartment(compartmentId)
				.orElseThrow(() -> new IllegalArgumentException("inventory.compartment.unknown"));
		if (compartments.size() == 1) {
			throw new InventoryRuleViolation("inventory.freezer.compartment-required");
		}
		compartments.remove(compartment);
	}

	/**
	 * Puts the compartments into a new order, e.g. after a member dragged a drawer to another position.
	 *
	 * @param order every compartment of this freezer, exactly once
	 */
	public void rearrangeCompartments(List<CompartmentId> order) {
		requireNotRemoved();
		var current = compartments.stream().map(Compartment::id).collect(Collectors.toSet());
		if (order.size() != compartments.size() || !current.equals(Set.copyOf(order))) {
			throw new IllegalArgumentException("inventory.compartment.order-invalid");
		}
		var byId = compartments.stream().collect(Collectors.toMap(Compartment::id, Function.identity()));
		compartments.clear();
		order.forEach(id -> compartments.add(byId.get(id)));
	}

	/**
	 * @throws IllegalArgumentException if the compartment does not belong to this freezer
	 */
	public Location locationOf(CompartmentId compartmentId) {
		requireNotRemoved();
		return compartment(compartmentId)
				.map(compartment -> new Location(id, compartment.id()))
				.orElseThrow(() -> new IllegalArgumentException("inventory.compartment.unknown"));
	}

	public Optional<Compartment> compartment(CompartmentId compartmentId) {
		return compartments.stream().filter(compartment -> compartment.id().equals(compartmentId)).findFirst();
	}

	/**
	 * Whether an item can be stored at this location — e.g. put back after an undone take-out: the freezer is still in
	 * use and still has the compartment.
	 */
	public boolean canStore(Location location) {
		return !isRemoved() && id.equals(location.freezerId()) && compartment(location.compartmentId()).isPresent();
	}

	/**
	 * Removes the freezer. The caller must make sure it is empty.
	 */
	public void remove(Instant now) {
		requireNotRemoved();
		removedAt = now;
	}

	public boolean isRemoved() {
		return removedAt != null;
	}

	public boolean belongsTo(HouseholdId householdId) {
		return this.householdId.equals(householdId);
	}

	private void requireNotRemoved() {
		if (isRemoved()) {
			throw new InventoryRuleViolation("inventory.freezer.removed", name);
		}
	}

	@Override
	public FreezerId getId() {
		return id;
	}

	public HouseholdId getHouseholdId() {
		return householdId;
	}

	public String getName() {
		return name;
	}

	public List<Compartment> getCompartments() {
		return Collections.unmodifiableList(compartments);
	}

	private static boolean sameName(String a, String b) {
		return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
	}

	private static String validName(String name, String what) {
		var trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(what + ".name-blank");
		}
		if (trimmed.length() > MAX_NAME_LENGTH) {
			throw new InvalidValueException(what + ".name-too-long", MAX_NAME_LENGTH);
		}
		return trimmed;
	}

}
