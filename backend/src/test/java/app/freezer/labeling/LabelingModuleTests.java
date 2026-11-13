package app.freezer.labeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdAccess;
import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemDirectory;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.ItemConsumed;
import app.freezer.inventory.ItemFrozen;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.LabelKind;
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

	@Autowired
	LabelSheets sheets;

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

}
