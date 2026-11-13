package app.freezer.labeling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import app.freezer.household.HouseholdId;
import app.freezer.labeling.domain.PrintJob.Placement;
import app.freezer.sharedkernel.LabelCode;

class LabelSheetTests {

	static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
	static final Sticker A = Sticker.qrOnly(LabelCode.of("AAAAAAAA"));
	static final Sticker B = Sticker.qrAndContent(LabelCode.of("BBBBBBBB"), "Peas", TODAY, TODAY.plusMonths(12));
	static final Sticker C = Sticker.contentOnly(LabelCode.of("CCCCCCCC"), "Goulash", TODAY, TODAY.plusMonths(3));

	LabelSheet sheet = LabelSheet.startFor(HouseholdId.random());

	@Test
	void printsOnTheNextFreePositionsOfTheCurrentSheet() {
		sheet.enqueue(A);
		sheet.print();
		sheet.enqueue(B);
		sheet.enqueue(C);

		var job = sheet.print();

		assertThat(job.pages()).containsExactly(List.of(new Placement(1, B), new Placement(2, C)));
		assertThat(sheet.getUsedPositions()).containsExactly(0, 1, 2);
		assertThat(sheet.getQueue()).isEmpty();
	}

	@Test
	void continuesOnANewSheetWhenTheCurrentOneIsFull() {
		IntStream.range(0, 23).forEach(sheet::togglePosition);
		sheet.enqueue(A);
		sheet.enqueue(B);
		sheet.enqueue(C);

		var job = sheet.print();

		assertThat(job.pages()).hasSize(2);
		assertThat(job.pages().get(0)).containsExactly(new Placement(23, A));
		assertThat(job.pages().get(1)).containsExactly(new Placement(0, B), new Placement(1, C));
		assertThat(sheet.getUsedPositions()).containsExactly(0, 1);
	}

	@Test
	void skipsPositionsMarkedAsUnusable() {
		sheet.togglePosition(0);
		sheet.togglePosition(1);
		sheet.togglePosition(1);
		sheet.enqueue(A);

		assertThat(sheet.print().pages().getFirst()).containsExactly(new Placement(1, A));
	}

	@Test
	void queuesTheSameLabelInTheSameLayoutOnlyOnce() {
		var code = LabelCode.of("DDDDDDDD");
		sheet.enqueue(Sticker.qrOnly(code));
		sheet.enqueue(Sticker.qrOnly(code));
		sheet.enqueue(Sticker.contentOnly(code, "Goulash", TODAY, TODAY.plusMonths(3)));

		assertThat(sheet.getQueue()).extracting(Sticker::layout)
				.containsExactly(StickerLayout.QR_ONLY, StickerLayout.CONTENT_ONLY);

		sheet.dequeue(code, StickerLayout.QR_ONLY);
		assertThat(sheet.getQueue()).extracting(Sticker::layout).containsExactly(StickerLayout.CONTENT_ONLY);
	}

	@Test
	void stickersWithContentsNeedATitle() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> Sticker.contentOnly(LabelCode.of("EEEEEEEE"), " ", TODAY, TODAY));
	}

	@Test
	void needsStickersToPrint() {
		assertThatThrownBy(sheet::print).isInstanceOf(LabelingRuleViolation.class);
	}

	@Test
	void newSheetStartsAtTheFirstPosition() {
		sheet.togglePosition(0);
		sheet.startNewSheet();

		assertThat(sheet.nextFreePosition()).isZero();
	}

}
