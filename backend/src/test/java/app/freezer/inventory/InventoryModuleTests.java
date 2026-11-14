package app.freezer.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.Catalog;
import app.freezer.catalog.ProductDescription;
import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.household.HouseholdMembers;
import app.freezer.household.UserId;
import app.freezer.inventory.application.FreezeCommand;
import app.freezer.inventory.application.FreezerManagement;
import app.freezer.inventory.application.FrozenItemManagement;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.inventory.domain.InventoryRuleViolation;
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

	@Autowired
	PlatformTransactionManager transactions;

	HouseholdMember alice;
	ProductId peas;
	FreezerId kitchen;
	CompartmentId top;

	@BeforeEach
	void setUpHouseholdWithFreezerAndPeas() {
		alice = HouseholdMembers.member(HouseholdId.random(), UserId.of("alice"), "Alice");
		peas = ProductId.random();
		when(catalog.findProduct(alice.householdId(), peas))
				.thenReturn(Optional.of(new ProductDescription(peas, "Peas", Period.ofMonths(12))));
		kitchen = freezers.setUpFreezer(alice, "Kitchen", List.of("Top", "Bottom"));
		top = freezers.overview(alice).getFirst().compartments().getFirst().id();
	}

	@Test
	void takingOutTheLastPortionPublishesItemConsumed(Scenario scenario) {
		var itemId = items.freeze(alice, freezePeasInto(top));

		scenario.stimulate(() -> items.takeOut(alice, itemId, 1))
				.andWaitForEventOfType(ItemConsumed.class)
				.matching(event -> event.itemId().equals(itemId))
				.toArriveAndVerify(event -> assertThat(event.productId()).isEqualTo(peas));
	}

	@Test
	void aCompartmentIsNotRemovedWhileAnItemIsPutIntoIt() throws Exception {
		var frozen = new CountDownLatch(1);
		var commit = new CountDownLatch(1);
		var transaction = new TransactionTemplate(transactions);

		try (var members = Executors.newVirtualThreadPerTaskExecutor()) {
			try {
				// Alice freezes peas into the top drawer; her transaction has not committed yet …
				var freezing = members.submit(() -> transaction.execute(status -> {
					var itemId = items.freeze(alice, freezePeasInto(top));
					frozen.countDown();
					awaitUninterruptibly(commit);
					return itemId;
				}));
				assertThat(frozen.await(10, TimeUnit.SECONDS)).isTrue();

				// … when Bob removes the top drawer. He waits for her, then sees the peas.
				var bob = HouseholdMembers.member(alice.householdId(), UserId.of("bob"), "Bob");
				var removing = members.submit(() -> freezers.removeCompartment(bob, kitchen, top));
				assertThatThrownBy(() -> removing.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
				commit.countDown();

				assertThat(freezing.get(10, TimeUnit.SECONDS)).isNotNull();
				assertThatThrownBy(() -> removing.get(10, TimeUnit.SECONDS))
						.isInstanceOf(ExecutionException.class)
						.cause().isInstanceOf(InventoryRuleViolation.class)
						.hasMessage("inventory.compartment.not-empty");
			}
			finally {
				commit.countDown();
			}
		}
	}

	private FreezeCommand freezePeasInto(CompartmentId compartment) {
		return new FreezeCommand(peas, kitchen, compartment, 1, null, LocalDate.now(), null, null, null, null);
	}

	private static void awaitUninterruptibly(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
