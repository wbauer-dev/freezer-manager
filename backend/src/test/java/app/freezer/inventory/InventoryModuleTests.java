package app.freezer.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.Catalog;
import app.freezer.catalog.ProductDescription;
import app.freezer.catalog.ProductId;
import app.freezer.catalog.ProductRenamed;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.household.HouseholdMembers;
import app.freezer.household.UserId;
import app.freezer.inventory.application.FreezeCommand;
import app.freezer.inventory.application.FreezerManagement;
import app.freezer.inventory.application.FrozenItemManagement;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.inventory.domain.FrozenItem;
import app.freezer.inventory.domain.FrozenItems;
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

	@MockitoSpyBean
	FrozenItems frozenItems;

	HouseholdMember alice;
	ProductId peas;
	FreezerId kitchen;
	CompartmentId top;

	@BeforeEach
	void setUpHouseholdWithFreezerAndPeas() {
		alice = HouseholdMembers.member(HouseholdId.random(), UserId.of("alice"), "Alice");
		peas = ProductId.random();
		knowPeasAs("Peas");
		kitchen = freezers.setUpFreezer(alice, "Kitchen", List.of("Top", "Bottom"));
		top = freezers.overview(alice).getFirst().compartments().getFirst().id();
	}

	@Test
	void takingOutTheLastPortionPublishesItemConsumed(Scenario scenario) {
		var itemId = items.freeze(alice, freezePeasInto(top));

		scenario.stimulate(() -> items.takeOut(alice, itemId, 1))
				.andWaitForEventOfType(ItemConsumed.class)
				.matching(event -> event.itemId().equals(itemId))
				.toArriveAndVerify(event -> {
					assertThat(event.productId()).isEqualTo(peas);
					// The item was saved and loaded in between: frozen (1), portions taken out (2), consumed (3).
					assertThat(event.revision()).isEqualTo(3);
				});
	}

	@Test
	void itemsTakeTheNameTheProductHasInTheCatalogNow(Scenario scenario) {
		var itemId = items.freeze(alice, freezePeasInto(top));
		knowPeasAs("Garden peas");

		// An event delivered late, after the product was renamed again
		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Petits pois"))
				.andWaitForStateChange(() -> nameOf(itemId), name -> !"Peas".equals(name))
				.andVerify(name -> assertThat(name).isEqualTo("Garden peas"));
	}

	@Test
	void itemsOfAProductRemovedFromTheCatalogTakeTheNameFromTheEvent(Scenario scenario) {
		var itemId = items.freeze(alice, freezePeasInto(top));
		when(catalog.findProduct(alice.householdId(), peas)).thenReturn(Optional.empty());

		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Garden peas"))
				.andWaitForStateChange(() -> nameOf(itemId), name -> !"Peas".equals(name))
				.andVerify(name -> assertThat(name).isEqualTo("Garden peas"));
	}

	@Test
	void aRenameThatCollidedWithATakeOutIsAppliedAgainWithoutHoldingUpOtherItems(Scenario scenario) {
		var first = items.freeze(alice, freezePeasInto(top));
		var second = items.freeze(alice, freezePeasInto(top));
		knowPeasAs("Garden peas");
		// The repository is an interface; its spy delegates to the real repository through its default answer.
		var realRepository = mockingDetails(frozenItems).getMockCreationSettings().getDefaultAnswer();
		doThrow(new OptimisticLockingFailureException("collided")).doAnswer(realRepository)
				.when(frozenItems).save(any());
		clearInvocations(frozenItems);

		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Garden peas"))
				.andWaitForStateChange(() -> List.of(nameOf(first), nameOf(second)),
						names -> names.stream().allMatch("Garden peas"::equals))
				.andVerify(names -> verify(frozenItems, times(3)).save(any(FrozenItem.class)));
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

	private void knowPeasAs(String name) {
		when(catalog.findProduct(alice.householdId(), peas))
				.thenReturn(Optional.of(new ProductDescription(peas, name, Period.ofMonths(12))));
	}

	private String nameOf(FrozenItemId itemId) {
		return items.details(alice, itemId).item().getProductName();
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
