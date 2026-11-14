package app.freezer.labeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdAccess;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMembers;
import app.freezer.household.UserId;
import app.freezer.inventory.FrozenItemDirectory;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.ItemConsumed;
import app.freezer.inventory.ItemDescription;
import app.freezer.inventory.ItemFrozen;
import app.freezer.labeling.application.FreeLabel;
import app.freezer.labeling.application.LabelPrinting;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.LabelKind;
import app.freezer.labeling.domain.LabelSheet;
import app.freezer.labeling.domain.LabelSheets;
import app.freezer.labeling.domain.LabelState;
import app.freezer.labeling.domain.Labels;
import app.freezer.labeling.domain.Sticker;
import app.freezer.sharedkernel.LabelCode;

/**
 * Bootstraps Labeling alone and drives it with Inventory's events only — no Inventory code runs. This is the whole
 * contract between the two contexts.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LabelingModuleTests {

	static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

	@MockitoBean
	HouseholdAccess householdAccess;

	@MockitoBean
	FrozenItemDirectory items;

	@Autowired
	Labels labels;

	@MockitoSpyBean
	LabelSheets sheets;

	@Autowired
	LabelPrinting printing;

	@Test
	void newItemGetsAOneTimeLabelWhoseStickerWaitsInThePrintQueue(Scenario scenario) {
		var household = HouseholdId.random();
		var code = LabelCode.generate();
		var item = FrozenItemId.random();
		var product = ProductId.random();

		scenario.publish(new ItemFrozen(item, household, product, "Peas", 3, TODAY, TODAY.plusMonths(12), code))
				.andWaitForStateChange(() -> labels.findById(code))
				.andVerify(label -> {
					assertThat(label).hasValueSatisfying(it -> {
						assertThat(it.getKind()).isEqualTo(LabelKind.ONE_TIME);
						assertThat(it.getState()).isEqualTo(LabelState.ATTACHED);
					});
					assertThat(sheets.findById(household).orElseThrow().getQueue()).containsExactly(
							Sticker.qrAndContent(code, "Peas", TODAY, TODAY.plusMonths(12)));
				});
	}

	@Test
	void aNewLabelWhoseStickerCollidedWithAChangeToTheSheetIsSavedAgain(Scenario scenario) {
		var household = HouseholdId.random();
		var code = LabelCode.generate();
		collideOnceOnTheSheet();

		scenario.publish(new ItemFrozen(FrozenItemId.random(), household, ProductId.random(), "Peas", 3, TODAY,
				TODAY.plusMonths(12), code))
				.andWaitForStateChange(() -> labels.findById(code))
				.andVerify(label -> {
					assertThat(label).isPresent();
					assertThat(sheets.findById(household).orElseThrow().getQueue()).extracting(Sticker::code)
							.containsExactly(code);
					verify(sheets, times(2)).save(any(LabelSheet.class));
				});
	}

	@Test
	void persistentLabelsWhoseStickersCollidedWithAChangeToTheSheetAreCreatedOnce() {
		var alice = HouseholdMembers.member(HouseholdId.random(), UserId.of("alice"), "Alice");
		collideOnceOnTheSheet();

		var codes = printing.createPersistentLabels(alice, 2);

		assertThat(printing.stock(alice).freePersistent()).isEqualTo(2);
		assertThat(printing.overview(alice).queue()).extracting(Sticker::code).containsExactlyElementsOf(codes);
		verify(sheets, times(2)).save(any(LabelSheet.class));
	}

	@Test
	void freeLabelsAskInventoryOnceWhatTheyHeldLast() {
		var alice = HouseholdMembers.member(HouseholdId.random(), UserId.of("alice"), "Alice");
		var peas = new ItemDescription(FrozenItemId.random(), "Peas", TODAY, TODAY.plusMonths(12));
		var goulash = new ItemDescription(FrozenItemId.random(), "Goulash", TODAY, TODAY.plusMonths(3));
		usedOnceFor(alice.householdId(), peas.itemId());
		usedOnceFor(alice.householdId(), goulash.itemId());
		labels.save(Label.persistent(alice.householdId(), Instant.now()));
		when(items.describeAll(eq(alice.householdId()), anyCollection()))
				.thenReturn(Map.of(peas.itemId(), peas, goulash.itemId(), goulash));

		assertThat(printing.freeLabels(alice)).extracting(FreeLabel::lastContents)
				.containsExactlyInAnyOrder("Peas", "Goulash", null);
		verify(items).describeAll(eq(alice.householdId()),
				argThat(ids -> Set.copyOf(ids).equals(Set.of(peas.itemId(), goulash.itemId()))));
		verify(items, never()).describe(any(), any());
	}

	@Test
	void labelIsUsedUpWhenTheItemIsEaten(Scenario scenario) {
		var household = HouseholdId.random();
		var code = LabelCode.generate();
		var item = FrozenItemId.random();
		var product = ProductId.random();

		scenario.publish(new ItemFrozen(item, household, product, "Peas", 1, TODAY, TODAY.plusMonths(12), code))
				.andWaitForStateChange(() -> labels.findById(code))
				.andVerify(label -> assertThat(label).isPresent());

		scenario.publish(new ItemConsumed(item, household, product, code))
				.andWaitForStateChange(() -> labels.findById(code).map(Label::getState),
						state -> state.filter(LabelState.USED_UP::equals).isPresent())
				.andVerify(state -> assertThat(state).contains(LabelState.USED_UP));
	}

	/** A persistent label that held the item and is free again. */
	private void usedOnceFor(HouseholdId household, FrozenItemId item) {
		var label = Label.persistent(household, Instant.now());
		label.attach(item);
		label.release(item);
		labels.save(label);
	}

	/** The first save of a sheet fails as if another member had changed it a moment earlier. */
	private void collideOnceOnTheSheet() {
		// The repository is an interface; its spy delegates to the real repository through its default answer.
		var realRepository = mockingDetails(sheets).getMockCreationSettings().getDefaultAnswer();
		doThrow(new OptimisticLockingFailureException("collided")).doAnswer(realRepository).when(sheets).save(any());
	}

}
