package app.freezer.labeling.domain;

import java.time.LocalDate;

import org.jmolecules.ddd.types.ValueObject;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.sharedkernel.LabelCode;

/**
 * One sticker waiting to be printed. The label is only the identity behind the QR code; what goes on the sticker is
 * decided per print, as a snapshot of the item at that moment.
 */
@Table(schema = "labeling", name = "queued_sticker")
public record Sticker(LabelCode code, StickerLayout layout, String title, LocalDate frozenOn, LocalDate bestBefore)
		implements ValueObject {

	public Sticker {
		if (code == null || layout == null) {
			throw new IllegalArgumentException("A sticker needs a label code and a layout");
		}
		if (layout != StickerLayout.QR_ONLY && (title == null || title.isBlank())) {
			throw new IllegalArgumentException("A sticker with contents needs a title");
		}
	}

	public static Sticker qrOnly(LabelCode code) {
		return new Sticker(code, StickerLayout.QR_ONLY, null, null, null);
	}

	public static Sticker qrAndContent(LabelCode code, String title, LocalDate frozenOn, LocalDate bestBefore) {
		return new Sticker(code, StickerLayout.QR_AND_CONTENT, title, frozenOn, bestBefore);
	}

	public static Sticker contentOnly(LabelCode code, String title, LocalDate frozenOn, LocalDate bestBefore) {
		return new Sticker(code, StickerLayout.CONTENT_ONLY, title, frozenOn, bestBefore);
	}

	public boolean showsQrCode() {
		return layout != StickerLayout.CONTENT_ONLY;
	}

	public boolean showsContent() {
		return layout != StickerLayout.QR_ONLY;
	}

	/** The same label in the same layout — queued at most once. */
	boolean sameAs(Sticker other) {
		return code.equals(other.code) && layout == other.layout;
	}

}
