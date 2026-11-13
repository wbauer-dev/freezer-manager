package app.freezer.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.HouseholdMember;
import app.freezer.inventory.application.FreezerOverview.CompartmentOverview;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.Freezer;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.inventory.domain.Freezers;
import app.freezer.inventory.domain.FrozenItems;
import app.freezer.inventory.domain.InventoryRuleViolation;

/**
 * Use cases for setting up freezers and their compartments.
 */
@Service
@Transactional
public class FreezerManagement {

	private final Freezers freezers;
	private final FrozenItems items;
	private final InventoryQueries queries;
	private final Clock clock;

	FreezerManagement(Freezers freezers, FrozenItems items, InventoryQueries queries, Clock clock) {
		this.freezers = freezers;
		this.items = items;
		this.queries = queries;
		this.clock = clock;
	}

	public FreezerId setUpFreezer(HouseholdMember member, String name, List<String> compartmentNames) {
		var freezer = Freezer.setUp(member.householdId(), name, compartmentNames);
		// Unique names concern all freezers of the household: checked here, backed by a unique index.
		if (freezers.existsWithName(member.householdId(), freezer.getName())) {
			throw new InventoryRuleViolation("inventory.freezer.name-taken", freezer.getName());
		}
		return freezers.save(freezer).getId();
	}

	public void addCompartment(HouseholdMember member, FreezerId freezerId, String name) {
		var freezer = freezerOf(member, freezerId);
		freezer.addCompartment(name);
		freezers.save(freezer);
	}

	public void removeCompartment(HouseholdMember member, FreezerId freezerId, CompartmentId compartmentId) {
		var freezer = freezerOf(member, freezerId);
		// Deliberately not an invariant of the Freezer aggregate: it would have to load all items to know.
		if (items.anyInCompartment(compartmentId)) {
			throw new InventoryRuleViolation("inventory.compartment.not-empty");
		}
		freezer.removeCompartment(compartmentId);
		freezers.save(freezer);
	}

	public void removeFreezer(HouseholdMember member, FreezerId freezerId) {
		var freezer = freezerOf(member, freezerId);
		// Like for compartments: whether the freezer is empty is a question about the items, so it is asked here.
		if (items.anyInFreezer(freezerId)) {
			throw new InventoryRuleViolation("inventory.freezer.not-empty");
		}
		freezer.remove(Instant.now(clock));
		freezers.save(freezer);
	}

	public void rearrangeCompartments(HouseholdMember member, FreezerId freezerId, List<CompartmentId> order) {
		var freezer = freezerOf(member, freezerId);
		freezer.rearrangeCompartments(order);
		freezers.save(freezer);
	}

	@Transactional(readOnly = true)
	public List<FreezerOverview> overview(HouseholdMember member) {
		var counts = queries.itemCountsByCompartment(member.householdId());
		return freezers.findAllOfHousehold(member.householdId()).stream()
				.map(freezer -> new FreezerOverview(freezer.getId(), freezer.getName(),
						freezer.getCompartments().stream()
								.map(compartment -> new CompartmentOverview(compartment.id(), compartment.name(),
										counts.getOrDefault(compartment.id(), 0)))
								.toList()))
				.toList();
	}

	private Freezer freezerOf(HouseholdMember member, FreezerId freezerId) {
		return freezers.findById(freezerId)
				.filter(freezer -> freezer.belongsTo(member.householdId()) && !freezer.isRemoved())
				.orElseThrow(() -> new InventoryNotFoundException("inventory.freezer.not-found"));
	}

}
