package app.freezer.labeling.application;

import java.util.Locale;

import app.freezer.labeling.domain.PrintJob;

/**
 * Renders a print job, e.g. as PDF. Implemented by the printing adapter.
 */
public interface LabelSheetRenderer {

	/** @param locale language of the texts and dates on the stickers */
	byte[] render(PrintJob job, Locale locale);

}
