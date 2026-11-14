package app.freezer.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Period;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.Catalog;
import app.freezer.catalog.ProductDescription;
import app.freezer.catalog.ProductId;
import app.freezer.catalog.ProductRenamed;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.household.HouseholdMembers;
import app.freezer.household.UserId;
import app.freezer.shopping.application.AddEntry;
import app.freezer.shopping.application.ShoppingListManagement;
import app.freezer.shopping.domain.Entry;
import app.freezer.shopping.domain.ShoppingList;
import app.freezer.shopping.domain.ShoppingLists;

/**
 * Bootstraps the Shopping module alone. Its only dependency on another module's beans — {@link Catalog} — is mocked.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ShoppingModuleTests {

	@MockitoBean
	Catalog catalog;

	@MockitoSpyBean
	ShoppingLists lists;

	@Autowired
	ShoppingListManagement shopping;

	@Autowired
	EventPublicationRegistry publications;

	@Autowired
	CompletedEventPublications completedPublications;

	HouseholdMember alice;
	ProductId peas;

	@BeforeEach
	void newHousehold() {
		alice = HouseholdMembers.member(HouseholdId.random(), UserId.of("alice"), "Alice");
		peas = ProductId.random();
	}

	@Test
	void productsAreListedUnderTheirNameInTheCatalog() {
		knowPeas();

		var list = shopping.add(alice, new AddEntry(peas, null, "500 g"));

		assertThat(list.getEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.productId()).isEqualTo(peas);
			assertThat(entry.name()).isEqualTo("Peas");
			assertThat(entry.addedByName()).isEqualTo("Alice");
		});
		assertThat(shopping.list(alice).getEntries()).hasSize(1);
	}

	@Test
	void productsOutsideTheHouseholdsCatalogAreRejected() {
		when(catalog.findProduct(alice.householdId(), peas)).thenReturn(Optional.empty());

		assertThatIllegalArgumentException().isThrownBy(() -> shopping.add(alice, new AddEntry(peas, null, null)))
				.withMessage("shopping.product.unknown");
	}

	@Test
	void entriesFollowRenamedProducts(Scenario scenario) {
		knowPeas();
		shopping.add(alice, new AddEntry(peas, null, null));
		catalogCalls(peas, "Garden peas");

		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Garden peas"))
				.andWaitForStateChange(this::nameOnTheList, name -> name.filter("Garden peas"::equals).isPresent())
				.andVerify(name -> assertThat(name).contains("Garden peas"));
	}

	/** Events may arrive late, twice or out of order: the list follows the catalog, not the event. */
	@Test
	void anOldRenameArrivingAfterANewerOneLeavesTheNewerName(Scenario scenario) {
		knowPeas();
		shopping.add(alice, new AddEntry(peas, null, null));
		catalogCalls(peas, "Frozen peas");
		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Frozen peas"))
				.andWaitForStateChange(this::nameOnTheList, name -> name.filter("Frozen peas"::equals).isPresent());

		var late = new ProductRenamed(alice.householdId(), peas, "Garden peas");
		scenario.publish(late)
				.andWaitForStateChange(() -> handled(late))
				.andVerify(handled -> assertThat(nameOnTheList()).contains("Frozen peas"));
	}

	/** The event publication registry only keeps what is outstanding (completion mode "delete"). */
	@Test
	void aHandledEventLeavesNoPublicationBehind(Scenario scenario) {
		knowPeas();
		shopping.add(alice, new AddEntry(peas, null, null));
		catalogCalls(peas, "Garden peas");

		var renamed = new ProductRenamed(alice.householdId(), peas, "Garden peas");
		scenario.publish(renamed)
				.andWaitForStateChange(() -> handled(renamed))
				.andVerify(handled -> assertThat(completedPublications.findAll())
						.noneMatch(publication -> publication.getEvent().equals(renamed)));
	}

	@Test
	void aProductRemovedFromTheCatalogKeepsTheNameFromTheEvent(Scenario scenario) {
		knowPeas();
		shopping.add(alice, new AddEntry(peas, null, null));
		when(catalog.findProduct(alice.householdId(), peas)).thenReturn(Optional.empty());

		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Garden peas"))
				.andWaitForStateChange(this::nameOnTheList, name -> name.filter("Garden peas"::equals).isPresent())
				.andVerify(name -> assertThat(name).contains("Garden peas"));
	}

	@Test
	void aRenameThatCollidedWithAMembersChangeIsAppliedAgain(Scenario scenario) {
		knowPeas();
		shopping.add(alice, new AddEntry(peas, null, null));
		catalogCalls(peas, "Garden peas");
		clearInvocations(lists);
		var realRepository = mockingDetails(lists).getMockCreationSettings().getDefaultAnswer();
		doThrow(new OptimisticLockingFailureException("collided")).doAnswer(realRepository).when(lists).save(any());

		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Garden peas"))
				.andWaitForStateChange(this::nameOnTheList, name -> name.filter("Garden peas"::equals).isPresent())
				.andVerify(name -> verify(lists, times(2)).save(any(ShoppingList.class)));
	}

	@Test
	void aChangeThatCollidedWithAnotherMembersChangeIsAppliedAgain() {
		shopping.add(alice, new AddEntry(null, "Milk", "1 l"));
		// The repository is an interface; its spy delegates to the real repository through its default answer.
		var realRepository = mockingDetails(lists).getMockCreationSettings().getDefaultAnswer();
		doThrow(new OptimisticLockingFailureException("collided")).doAnswer(realRepository).when(lists).save(any());

		var list = shopping.add(alice, new AddEntry(null, "Milk", "2 l"));

		assertThat(list.getEntries()).singleElement().extracting(Entry::quantity).isEqualTo("1 l + 2 l");
		verify(lists, times(3)).save(any(ShoppingList.class));
	}

	private void knowPeas() {
		catalogCalls(peas, "Peas");
	}

	private void catalogCalls(ProductId product, String name) {
		when(catalog.findProduct(alice.householdId(), product))
				.thenReturn(Optional.of(new ProductDescription(product, name, Period.ofMonths(12))));
	}

	private boolean handled(Object event) {
		return publications.findIncompletePublications().stream()
				.noneMatch(publication -> publication.getEvent().equals(event));
	}

	private Optional<String> nameOnTheList() {
		return lists.findById(alice.householdId())
				.flatMap(list -> list.getEntries().stream().findFirst())
				.map(Entry::name);
	}

}
