package app.freezer.labeling.domain;

import java.util.List;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Where each sticker goes: one list of placements per sheet.
 */
public record PrintJob(SheetFormat format, List<List<Placement>> pages) implements ValueObject {

	public PrintJob {
		pages = pages.stream().map(List::copyOf).toList();
	}

	/** A sticker and the position on the sheet where it is printed. */
	public record Placement(int position, Sticker sticker) implements ValueObject {
	}

}
