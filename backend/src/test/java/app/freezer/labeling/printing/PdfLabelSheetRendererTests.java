package app.freezer.labeling.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import app.freezer.labeling.domain.PrintJob;
import app.freezer.labeling.domain.PrintJob.Placement;
import app.freezer.labeling.domain.SheetFormat;
import app.freezer.labeling.domain.Sticker;
import app.freezer.sharedkernel.LabelCode;

/**
 * Prints stickers, renders the page like a printer would and reads it back like a phone camera would.
 */
class PdfLabelSheetRendererTests {

	static final int DPI = 150;
	static final SheetFormat FORMAT = SheetFormat.DEFAULT;
	static final LocalDate FROZEN_ON = LocalDate.of(2026, 11, 13);

	PdfLabelSheetRenderer renderer = new PdfLabelSheetRenderer("https://freezer.example/s/", messages());

	@Test
	void eachLayoutPrintsWhatItPromises() throws Exception {
		var oneTime = Sticker.qrAndContent(LabelCode.of("K7Q2M9XA"), "Kürbiscremesuppe", FROZEN_ON,
				FROZEN_ON.plusMonths(4));
		var persistent = Sticker.qrOnly(LabelCode.of("P3RS1ST3"));
		var contents = Sticker.contentOnly(LabelCode.of("P3RS1ST3"), "Beef goulash", FROZEN_ON,
				FROZEN_ON.plusMonths(3));
		var job = new PrintJob(FORMAT,
				List.of(List.of(new Placement(0, oneTime), new Placement(1, persistent), new Placement(2, contents))));

		try (var document = Loader.loadPDF(renderer.render(job, Locale.ENGLISH))) {
			var page = new PDFRenderer(document).renderImageWithDPI(0, DPI);
			assertThat(readQrCode(page, 0)).isEqualTo("https://freezer.example/s/K7Q2M9XA");
			assertThat(readQrCode(page, 1)).isEqualTo("https://freezer.example/s/P3RS1ST3");
			assertThatThrownBy(() -> readQrCode(page, 2)).isInstanceOf(NotFoundException.class);
			assertThat(new PDFTextStripper().getText(document)).contains("Kürbiscremesuppe", "Best before 13 Mar 2027",
					"K7Q2-M9XA", "P3RS-1ST3", "Scan to see", "Beef goulash", "Best before 13 Feb 2027",
					"Label P3RS-1ST3");
		}
	}

	@Test
	void printsInTheLanguageOfTheMember() throws Exception {
		var sticker = Sticker.contentOnly(LabelCode.of("P3RS1ST3"), "Rindsgulasch", FROZEN_ON, FROZEN_ON.plusMonths(3));
		var job = new PrintJob(FORMAT, List.of(List.of(new Placement(0, sticker))));

		try (var document = Loader.loadPDF(renderer.render(job, Locale.GERMAN))) {
			assertThat(new PDFTextStripper().getText(document)).contains("Rindsgulasch", "Eingefroren 13.11.2026",
					"Haltbar bis 13.02.2027", "Etikett P3RS-1ST3");
		}
	}

	private static MessageSource messages() {
		var messages = new ResourceBundleMessageSource();
		messages.setBasename("i18n/labeling");
		messages.setDefaultEncoding("UTF-8");
		messages.setFallbackToSystemLocale(false);
		return messages;
	}

	private static String readQrCode(BufferedImage page, int position) throws Exception {
		double pixelsPerMm = DPI / 25.4;
		var sticker = page.getSubimage((int) (FORMAT.x(position) * pixelsPerMm),
				(int) (FORMAT.y(position) * pixelsPerMm), (int) (FORMAT.labelWidth() * pixelsPerMm),
				(int) (FORMAT.labelHeight() * pixelsPerMm));
		int width = sticker.getWidth();
		int height = sticker.getHeight();
		var pixels = sticker.getRGB(0, 0, width, height, null, 0, width);
		var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
		return new QRCodeReader().decode(bitmap).getText();
	}

}
