package app.freezer.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import app.freezer.household.HouseholdId;

class FreezerTests {

	static final HouseholdId HOUSEHOLD = HouseholdId.random();

	@Test
	void isSetUpWithItsCompartments() {
		var freezer = Freezer.setUp(HOUSEHOLD, " Kitchen ", List.of("Top", "Bottom"));

		assertThat(freezer.getName()).isEqualTo("Kitchen");
		assertThat(freezer.getCompartments()).extracting(Compartment::name).containsExactly("Top", "Bottom");
	}

	@Test
	void needsAtLeastOneCompartment() {
		assertThatIllegalArgumentException().isThrownBy(() -> Freezer.setUp(HOUSEHOLD, "Kitchen", List.of()));
	}

	@Test
	void compartmentNamesAreUniqueIgnoringCase() {
		var freezer = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top"));

		assertThatThrownBy(() -> freezer.addCompartment("top"))
				.isInstanceOf(InventoryRuleViolation.class)
				.hasMessage("inventory.compartment.name-taken");
	}

	@Test
	void hasAtMostTwentyCompartments() {
		var names = IntStream.rangeClosed(1, Freezer.MAX_COMPARTMENTS).mapToObj(i -> "Drawer " + i).toList();
		var freezer = Freezer.setUp(HOUSEHOLD, "Chest freezer", names);

		assertThatThrownBy(() -> freezer.addCompartment("One more")).isInstanceOf(InventoryRuleViolation.class);
	}

	@Test
	void keepsItsLastCompartment() {
		var freezer = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top", "Bottom"));
		var top = freezer.getCompartments().getFirst();
		var bottom = freezer.getCompartments().getLast();

		freezer.removeCompartment(top.id());

		assertThat(freezer.getCompartments()).containsExactly(bottom);
		assertThatThrownBy(() -> freezer.removeCompartment(bottom.id()))
				.isInstanceOf(InventoryRuleViolation.class)
				.hasMessage("inventory.freezer.compartment-required");
	}

	@Test
	void compartmentsCanBeRearranged() {
		var freezer = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top", "Middle", "Bottom"));
		var top = freezer.getCompartments().get(0).id();
		var middle = freezer.getCompartments().get(1).id();
		var bottom = freezer.getCompartments().get(2).id();

		freezer.rearrangeCompartments(List.of(bottom, top, middle));

		assertThat(freezer.getCompartments()).extracting(Compartment::name).containsExactly("Bottom", "Top", "Middle");
	}

	@Test
	void newOrderNamesEveryCompartmentExactlyOnce() {
		var freezer = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top", "Bottom"));
		var top = freezer.getCompartments().get(0).id();
		var bottom = freezer.getCompartments().get(1).id();

		assertThatIllegalArgumentException().isThrownBy(() -> freezer.rearrangeCompartments(List.of(top)));
		assertThatIllegalArgumentException().isThrownBy(() -> freezer.rearrangeCompartments(List.of(top, top)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> freezer.rearrangeCompartments(List.of(top, CompartmentId.random())));
		assertThat(freezer.getCompartments()).extracting(Compartment::id).containsExactly(top, bottom);
	}

	@Test
	void removedFreezerCannotBeChangedOrUsed() {
		var freezer = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top", "Bottom"));
		var top = freezer.getCompartments().getFirst().id();

		freezer.remove(Instant.parse("2026-11-13T10:00:00Z"));

		assertThat(freezer.isRemoved()).isTrue();
		assertThatThrownBy(() -> freezer.addCompartment("Middle")).isInstanceOf(InventoryRuleViolation.class);
		assertThatThrownBy(() -> freezer.removeCompartment(top)).isInstanceOf(InventoryRuleViolation.class);
		assertThatThrownBy(() -> freezer.locationOf(top))
				.isInstanceOf(InventoryRuleViolation.class)
				.hasMessage("inventory.freezer.removed");
		assertThatThrownBy(() -> freezer.remove(Instant.now())).isInstanceOf(InventoryRuleViolation.class);
	}

	@Test
	void locationsOnlyNameOwnCompartments() {
		var kitchen = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top"));
		var cellar = Freezer.setUp(HOUSEHOLD, "Cellar", List.of("Top"));
		var kitchenTop = kitchen.getCompartments().getFirst().id();

		assertThat(kitchen.locationOf(kitchenTop)).isEqualTo(new Location(kitchen.getId(), kitchenTop));
		assertThatIllegalArgumentException().isThrownBy(() -> cellar.locationOf(kitchenTop));
	}

	@Test
	void storesItemsOnlyInExistingCompartmentsWhileInUse() {
		var kitchen = Freezer.setUp(HOUSEHOLD, "Kitchen", List.of("Top", "Bottom"));
		var cellar = Freezer.setUp(HOUSEHOLD, "Cellar", List.of("Top"));
		var top = kitchen.locationOf(kitchen.getCompartments().getFirst().id());
		var bottom = kitchen.locationOf(kitchen.getCompartments().getLast().id());

		assertThat(kitchen.canStore(top)).isTrue();
		assertThat(cellar.canStore(top)).isFalse();

		kitchen.removeCompartment(top.compartmentId());
		assertThat(kitchen.canStore(top)).isFalse();
		assertThat(kitchen.canStore(bottom)).isTrue();

		kitchen.remove(Instant.parse("2026-11-13T10:00:00Z"));
		assertThat(kitchen.canStore(bottom)).isFalse();
	}

}
