package app.freezer.labeling.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.HouseholdMember;
import app.freezer.inventory.FrozenItemDirectory;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.ItemDescription;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.LabelSheet;
import app.freezer.labeling.domain.LabelSheets;
import app.freezer.labeling.domain.LabelState;
import app.freezer.labeling.domain.Labels;
import app.freezer.labeling.domain.Sticker;
import app.freezer.labeling.domain.StickerLayout;
import app.freezer.sharedkernel.InvalidValueException;
import app.freezer.sharedkernel.LabelCode;

/**
 * Use cases around the print queue, the household's A4 sticker sheet and its persistent labels.
 */
@Service
@Transactional
public class LabelPrinting {

	public static final int MAX_PERSISTENT_AT_ONCE = 48;

	private final Labels labels;
	private final LabelSheets sheets;
	private final LabelSheetRenderer renderer;
	private final FrozenItemDirectory items;
	private final Clock clock;

	LabelPrinting(Labels labels, LabelSheets sheets, LabelSheetRenderer renderer, FrozenItemDirectory items,
			Clock clock) {
		this.labels = labels;
		this.sheets = sheets;
		this.renderer = renderer;
		this.items = items;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public SheetOverview overview(HouseholdMember member) {
		var sheet = sheetOf(member);
		return new SheetOverview(sheet.getFormat(), sheet.getUsedPositions(), sheet.nextFreePosition(),
				sheet.getQueue());
	}

	/**
	 * The persistent labels that can be used for the next filling, each with what it held last (as Inventory knows
	 * it), so members can tell their containers apart.
	 */
	@Transactional(readOnly = true)
	public List<FreeLabel> freeLabels(HouseholdMember member) {
		return labels.findFreePersistent(member.householdId()).stream()
				.map(label -> new FreeLabel(label.getId(), label.getItemId()
						.flatMap(item -> items.describe(member.householdId(), item))
						.map(ItemDescription::productName)
						.orElse(null)))
				.toList();
	}

	@Transactional(readOnly = true)
	public LabelStock stock(HouseholdMember member) {
		return new LabelStock(labels.countPersistent(member.householdId(), LabelState.FREE),
				labels.countPersistent(member.householdId(), LabelState.ATTACHED));
	}

	/**
	 * Puts a sticker for a label into the print queue, e.g. to replace a smudged one or to show what is in a container
	 * with a persistent label. Stickers with contents describe the given item, or else the label's current one, as
	 * Inventory knows it now.
	 */
	public void enqueue(HouseholdMember member, LabelCode code, StickerLayout layout, FrozenItemId itemId) {
		var label = labelOf(member, code);
		var sticker = layout == StickerLayout.QR_ONLY ? Sticker.qrOnly(code)
				: contentSticker(member, label, layout, itemId);
		var sheet = sheetOf(member);
		sheet.enqueue(sticker);
		sheets.save(sheet);
	}

	public void dequeue(HouseholdMember member, LabelCode code, StickerLayout layout) {
		var sheet = sheetOf(member);
		sheet.dequeue(code, layout);
		sheets.save(sheet);
	}

	/**
	 * Places the queued stickers on the sheet and renders them in the given language. If rendering fails, nothing is
	 * marked as printed.
	 */
	public byte[] print(HouseholdMember member, Locale locale) {
		var sheet = sheetOf(member);
		var pdf = renderer.render(sheet.print(), locale);
		sheets.save(sheet);
		return pdf;
	}

	public void startNewSheet(HouseholdMember member) {
		var sheet = sheetOf(member);
		sheet.startNewSheet();
		sheets.save(sheet);
	}

	public void togglePosition(HouseholdMember member, int position) {
		var sheet = sheetOf(member);
		sheet.togglePosition(position);
		sheets.save(sheet);
	}

	/** Creates labels for containers, lids or bag clips and queues them for printing — QR code only. */
	public List<LabelCode> createPersistentLabels(HouseholdMember member, int count) {
		if (count < 1 || count > MAX_PERSISTENT_AT_ONCE) {
			throw new InvalidValueException("labeling.persistent.count-invalid", MAX_PERSISTENT_AT_ONCE);
		}
		var sheet = sheetOf(member);
		var codes = IntStream.range(0, count)
				.mapToObj(i -> labels.save(Label.persistent(member.householdId(), Instant.now(clock))).getId())
				.toList();
		codes.forEach(code -> sheet.enqueue(Sticker.qrOnly(code)));
		sheets.save(sheet);
		return codes;
	}

	private Sticker contentSticker(HouseholdMember member, Label label, StickerLayout layout, FrozenItemId itemId) {
		var item = itemId != null ? itemId
				: label.getItemId().orElseThrow(() -> new IllegalArgumentException("labeling.label.not-on-item"));
		var description = items.describe(member.householdId(), item)
				.orElseThrow(() -> new IllegalArgumentException("labeling.item.unknown"));
		return layout == StickerLayout.CONTENT_ONLY
				? Sticker.contentOnly(label.getId(), description.productName(), description.frozenOn(),
						description.bestBefore())
				: Sticker.qrAndContent(label.getId(), description.productName(), description.frozenOn(),
						description.bestBefore());
	}

	private LabelSheet sheetOf(HouseholdMember member) {
		return sheets.findById(member.householdId()).orElseGet(() -> LabelSheet.startFor(member.householdId()));
	}

	private Label labelOf(HouseholdMember member, LabelCode code) {
		return labels.findById(code)
				.filter(label -> label.belongsTo(member.householdId()))
				.orElseThrow(UnknownLabelException::new);
	}

}
