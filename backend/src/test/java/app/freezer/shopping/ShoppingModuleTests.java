package app.freezer.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
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

		scenario.publish(new ProductRenamed(alice.householdId(), peas, "Garden peas"))
				.andWaitForStateChange(this::nameOnTheList, name -> name.filter("Garden peas"::equals).isPresent())
				.andVerify(name -> assertThat(name).contains("Garden peas"));
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
		when(catalog.findProduct(alice.householdId(), peas))
				.thenReturn(Optional.of(new ProductDescription(peas, "Peas", Period.ofMonths(12))));
	}

	private Optional<String> nameOnTheList() {
		return lists.findById(alice.householdId())
				.flatMap(list -> list.getEntries().stream().findFirst())
				.map(Entry::name);
	}

}
