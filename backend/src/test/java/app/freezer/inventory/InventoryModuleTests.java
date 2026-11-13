package app.freezer.inventory;

import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.Catalog;
import app.freezer.catalog.ProductDescription;
import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMembers;
import app.freezer.household.UserId;
import app.freezer.inventory.application.FreezeCommand;
import app.freezer.inventory.application.FreezerManagement;
import app.freezer.inventory.application.FrozenItemManagement;
import app.freezer.media.MediaLibrary;

/**
 * Bootstraps the Inventory module alone. Its dependencies on other modules' beans — {@link Catalog} and
 * {@link MediaLibrary} — are mocked; the test shows that Inventory needs nothing else.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class InventoryModuleTests {

	@MockitoBean
	Catalog catalog;

	@MockitoBean
	MediaLibrary media;

	@Autowired
	FreezerManagement freezers;

	@Autowired
	FrozenItemManagement items;

	@Test
	void takingOutTheLastPortionPublishesItemConsumed(Scenario scenario) {
		var member = HouseholdMembers.member(HouseholdId.random(), UserId.of("alice"), "Alice");
		var peas = ProductId.random();
		when(catalog.findProduct(member.householdId(), peas))
				.thenReturn(Optional.of(new ProductDescription(peas, "Peas", Period.ofMonths(12))));
		var freezerId = freezers.setUpFreezer(member, "Kitchen", List.of("Top"));
		var compartmentId = freezers.overview(member).getFirst().compartments().getFirst().id();
		var itemId = items.freeze(member,
				new FreezeCommand(peas, freezerId, compartmentId, 1, null, LocalDate.now(), null, null, null, null));

		scenario.stimulate(() -> items.takeOut(member, itemId, 1))
				.andWaitForEventOfType(ItemConsumed.class)
				.matching(event -> event.itemId().equals(itemId))
				.toArriveAndVerify(event -> {
					org.assertj.core.api.Assertions.assertThat(event.productId()).isEqualTo(peas);
				});
	}

}
