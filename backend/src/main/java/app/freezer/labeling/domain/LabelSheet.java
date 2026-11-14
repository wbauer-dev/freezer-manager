package app.freezer.labeling.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.ValueObject;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.LabelCode;

/**
 * The household's current A4 sticker sheet and the stickers waiting to be printed.
 * <p>
 * Printing a whole sheet for one sticker would waste the rest, so the sheet remembers which positions are used. A
 * print run places the queued stickers on the next free positions; a partly used sheet simply goes through the
 * printer again. When a sheet is full, printing continues on a new one.
 */
@Table(schema = "labeling", name = "label_sheet")
public class LabelSheet implements AggregateRoot<LabelSheet, HouseholdId> {

	@Identity
	private final HouseholdId householdId;
	@MappedCollection(idColumn = "household_id")
	private final Set<UsedPosition> usedPositions;
	@MappedCollection(idColumn = "household_id", keyColumn = "queue_index")
	private final List<Sticker> queue;
	@Version
	private Long version;

	LabelSheet(HouseholdId householdId, Set<UsedPosition> usedPositions, List<Sticker> queue, Long version) {
		this.householdId = householdId;
		this.usedPositions = new HashSet<>(usedPositions);
		this.queue = new ArrayList<>(queue);
		this.version = version;
	}

	public static LabelSheet startFor(HouseholdId householdId) {
		return new LabelSheet(householdId, Set.of(), List.of(), null);
	}

	public SheetFormat getFormat() {
		return SheetFormat.DEFAULT;
	}

	/** Adds a sticker to the print queue. The same label in the same layout is queued at most once. */
	public void enqueue(Sticker sticker) {
		if (queue.stream().noneMatch(sticker::sameAs)) {
			queue.add(sticker);
		}
	}

	public void dequeue(LabelCode code, StickerLayout layout) {
		queue.removeIf(sticker -> sticker.code().equals(code) && sticker.layout() == layout);
	}

	/**
	 * Places all queued stickers: first on the free positions of the current sheet, then on new sheets. Empties the
	 * queue; the last sheet printed on becomes the current sheet. A sheet that is already full gets no page.
	 */
	public PrintJob print() {
		if (queue.isEmpty()) {
			throw new LabelingRuleViolation("labeling.sheet.nothing-to-print");
		}
		var pages = new ArrayList<List<PrintJob.Placement>>();
		var page = new ArrayList<PrintJob.Placement>();
		for (var sticker : queue) {
			if (nextFreePosition() < 0) {
				if (!page.isEmpty()) {
					pages.add(page);
					page = new ArrayList<>();
				}
				usedPositions.clear();
			}
			var position = nextFreePosition();
			usedPositions.add(new UsedPosition(position));
			page.add(new PrintJob.Placement(position, sticker));
		}
		pages.add(page);
		queue.clear();
		return new PrintJob(getFormat(), pages);
	}

	/** The sheet in the printer was replaced by a fresh one. */
	public void startNewSheet() {
		usedPositions.clear();
	}

	/** Marks a position as used, e.g. when the sticker there was torn or misprinted. */
	public void markUsed(int position) {
		usedPositions.add(validPosition(position));
	}

	/** Marks a position as free again, e.g. when it was marked by mistake. */
	public void markFree(int position) {
		usedPositions.remove(validPosition(position));
	}

	public boolean isUsed(int position) {
		return usedPositions.contains(new UsedPosition(position));
	}

	private UsedPosition validPosition(int position) {
		if (!getFormat().contains(position)) {
			throw new IllegalArgumentException("labeling.sheet.position-invalid");
		}
		return new UsedPosition(position);
	}

	/** The next free position on the current sheet, or -1 if it is full. */
	public int nextFreePosition() {
		for (int position = 0; position < getFormat().capacity(); position++) {
			if (!usedPositions.contains(new UsedPosition(position))) {
				return position;
			}
		}
		return -1;
	}

	@Override
	public HouseholdId getId() {
		return householdId;
	}

	public Set<Integer> getUsedPositions() {
		var positions = new TreeSet<Integer>();
		usedPositions.forEach(used -> positions.add(used.position()));
		return Collections.unmodifiableSet(positions);
	}

	public List<Sticker> getQueue() {
		return Collections.unmodifiableList(queue);
	}

	@Table(schema = "labeling", name = "used_position")
	record UsedPosition(int position) implements ValueObject {
	}

}
