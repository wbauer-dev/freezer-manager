package app.freezer.labeling.printing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import app.freezer.labeling.application.LabelSheetRenderer;
import app.freezer.labeling.domain.PrintJob;
import app.freezer.labeling.domain.SheetFormat;
import app.freezer.labeling.domain.Sticker;

/**
 * Renders A4 sticker sheets as PDF, one layout per sticker. QR codes are drawn as vector rectangles, so they stay sharp
 * on any printer.
 */
@Component
class PdfLabelSheetRenderer implements LabelSheetRenderer {

	private static final float MM = 72f / 25.4f;
	private static final float PADDING = 3 * MM;
	/** Large enough for any phone camera, small enough to leave room for the text. */
	private static final float MAX_QR_SIZE = 27 * MM;
	private static final float MIN_FONT_SIZE = 6;

	private final String scanUrl;
	private final MessageSource messages;

	PdfLabelSheetRenderer(@Value("${freezer.labels.scan-url}") String scanUrl, MessageSource messages) {
		this.scanUrl = scanUrl;
		this.messages = messages;
	}

	@Override
	public byte[] render(PrintJob job, Locale locale) {
		try (var document = new PDDocument()) {
			var fonts = new Fonts();
			var texts = new Texts(messages, locale);
			for (var page : job.pages()) {
				var pdfPage = new PDPage(PDRectangle.A4);
				document.addPage(pdfPage);
				try (var content = new PDPageContentStream(document, pdfPage)) {
					for (var placement : page) {
						var area = Area.of(job.format(), placement.position());
						switch (placement.sticker().layout()) {
							case QR_ONLY -> drawQrOnly(content, fonts, texts, area, placement.sticker());
							case QR_AND_CONTENT -> drawQrAndContent(content, fonts, texts, area, placement.sticker());
							case CONTENT_ONLY -> drawContentOnly(content, fonts, texts, area, placement.sticker());
						}
					}
				}
			}
			var pdf = new ByteArrayOutputStream();
			document.save(pdf);
			return pdf.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Stickers could not be rendered", e);
		}
	}

	/** Persistent label: printed once, so the QR code gets all the room; the contents live in the app. */
	private void drawQrOnly(PDPageContentStream content, Fonts fonts, Texts texts, Area area, Sticker sticker)
			throws IOException {
		float qrSize = area.height - 2 * PADDING;
		drawQrCode(content, scanUrl + sticker.code().value(), area.left + PADDING, area.bottom() + PADDING, qrSize);

		float textLeft = area.left + PADDING + qrSize + 2.5f * MM;
		float textWidth = area.right() - PADDING - textLeft;
		float middle = area.bottom() + area.height / 2;
		fittedText(content, fonts.code, 13, textLeft, middle + 4, textWidth, sticker.code().formatted());
		fittedText(content, fonts.regular, 7.5f, textLeft, middle - 9, textWidth,
				texts.get("labeling.sticker.scan-to-see"));
		fittedText(content, fonts.regular, 7.5f, textLeft, middle - 18, textWidth,
				texts.get("labeling.sticker.whats-inside"));
	}

	/** One-time label: QR code with the item's name and dates. */
	private void drawQrAndContent(PDPageContentStream content, Fonts fonts, Texts texts, Area area, Sticker sticker)
			throws IOException {
		float qrSize = Math.min(area.height - 2 * PADDING, MAX_QR_SIZE);
		drawQrCode(content, scanUrl + sticker.code().value(), area.left + PADDING,
				area.bottom() + (area.height - qrSize) / 2, qrSize);

		float textLeft = area.left + PADDING + qrSize + 2.5f * MM;
		float textWidth = area.right() - PADDING - textLeft;
		float y = drawTitle(content, fonts, sticker.title(), 10, textLeft, area.top - PADDING - 9, textWidth);
		drawDates(content, fonts, texts, sticker, 7.5f, textLeft, y - 1, textWidth);
		fittedText(content, fonts.code, 9, textLeft, area.bottom() + PADDING + 1, textWidth,
				sticker.code().formatted());
	}

	/** For a container with a persistent label: what is inside, readable without a phone. */
	private void drawContentOnly(PDPageContentStream content, Fonts fonts, Texts texts, Area area, Sticker sticker)
			throws IOException {
		float textLeft = area.left + PADDING + 1 * MM;
		float textWidth = area.width - 2 * PADDING - 2 * MM;
		float y = drawTitle(content, fonts, sticker.title(), 14, textLeft, area.top - PADDING - 13, textWidth);
		drawDates(content, fonts, texts, sticker, 9, textLeft, y - 2, textWidth);
		fittedText(content, fonts.regular, 7, textLeft, area.bottom() + PADDING + 1, textWidth,
				texts.get("labeling.sticker.label", sticker.code().formatted()));
	}

	/** Draws the title on up to two lines, smaller if a single word is too long; returns the next baseline. */
	private static float drawTitle(PDPageContentStream content, Fonts fonts, String title, float size, float x, float y,
			float width) throws IOException {
		float titleSize = fitsWordByWord(title, fonts.bold, size, width) ? size : size * 0.8f;
		float baseline = y;
		for (var line : wrap(title, fonts.bold, titleSize, width, 2)) {
			text(content, fonts.bold, titleSize, x, baseline, line);
			baseline -= titleSize + 1.5f;
		}
		return baseline;
	}

	private static void drawDates(PDPageContentStream content, Fonts fonts, Texts texts, Sticker sticker, float size,
			float x, float y, float width) throws IOException {
		float baseline = y;
		if (sticker.frozenOn() != null) {
			fittedText(content, fonts.regular, size, x, baseline, width,
					texts.get("labeling.sticker.frozen", texts.date(sticker.frozenOn())));
			baseline -= size + 1.5f;
		}
		if (sticker.bestBefore() != null) {
			fittedText(content, fonts.bold, size, x, baseline, width,
					texts.get("labeling.sticker.best-before", texts.date(sticker.bestBefore())));
		}
	}

	/** A sticker's rectangle on the page, in PDF points (origin bottom left). */
	private record Area(float left, float top, float width, float height) {

		static Area of(SheetFormat format, int position) {
			return new Area((float) format.x(position) * MM, (float) (SheetFormat.A4_HEIGHT - format.y(position)) * MM,
					(float) format.labelWidth() * MM, (float) format.labelHeight() * MM);
		}

		float bottom() {
			return top - height;
		}

		float right() {
			return left + width;
		}
	}

	/** Shrinks the font down to {@link #MIN_FONT_SIZE} so the text fits; cuts it only if that is not enough. */
	private static void fittedText(PDPageContentStream content, PDFont font, float maxSize, float x, float y,
			float width, String text) throws IOException {
		var printable = printable(font, text);
		float size = maxSize;
		while (size > MIN_FONT_SIZE && width(font, size, printable) > width) {
			size -= 0.5f;
		}
		while (width(font, size, printable) > width && printable.length() > 1) {
			printable = printable.substring(0, printable.length() - 2) + "…";
		}
		text(content, font, size, x, y, printable);
	}

	private static boolean fitsWordByWord(String text, PDFont font, float size, float width) throws IOException {
		for (var word : printable(font, text).split("\\s+")) {
			if (width(font, size, word) > width) {
				return false;
			}
		}
		return true;
	}

	private static void drawQrCode(PDPageContentStream content, String data, float x, float y, float size)
			throws IOException {
		try {
			var matrix = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0,
					Map.of(EncodeHintType.MARGIN, 0, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
			float module = size / matrix.getWidth();
			for (int row = 0; row < matrix.getHeight(); row++) {
				for (int column = 0; column < matrix.getWidth(); column++) {
					if (matrix.get(column, row)) {
						content.addRect(x + column * module, y + size - (row + 1) * module, module, module);
					}
				}
			}
			content.fill();
		}
		catch (WriterException e) {
			throw new IllegalStateException("QR code could not be created", e);
		}
	}

	private static void text(PDPageContentStream content, PDFont font, float size, float x, float y, String text)
			throws IOException {
		content.beginText();
		content.setFont(font, size);
		content.newLineAtOffset(x, y);
		content.showText(printable(font, text));
		content.endText();
	}

	/** Breaks the text into at most {@code maxLines} lines that fit the width; the last line ends with … if cut. */
	private static List<String> wrap(String text, PDFont font, float size, float width, int maxLines)
			throws IOException {
		var lines = new ArrayList<String>();
		var line = new StringBuilder();
		for (var word : printable(font, text).split("\\s+")) {
			var candidate = line.isEmpty() ? word : line + " " + word;
			if (width(font, size, candidate) <= width) {
				line = new StringBuilder(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
			}
			line = new StringBuilder(word);
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		if (lines.size() > maxLines) {
			lines = new ArrayList<>(lines.subList(0, maxLines));
			lines.set(maxLines - 1, lines.get(maxLines - 1) + " …");
		}
		for (int i = 0; i < lines.size(); i++) {
			var shortened = lines.get(i);
			while (width(font, size, shortened) > width && shortened.length() > 1) {
				shortened = shortened.substring(0, shortened.length() - 2) + "…";
			}
			lines.set(i, shortened);
		}
		return lines;
	}

	private static float width(PDFont font, float size, String text) throws IOException {
		return font.getStringWidth(text) / 1000 * size;
	}

	/** The standard PDF fonts only know Western European characters; anything else is replaced. */
	private static String printable(PDFont font, String text) {
		var result = new StringBuilder();
		text.codePoints().forEach(codePoint -> {
			var character = new String(Character.toChars(codePoint));
			try {
				font.encode(character);
				result.append(character);
			}
			catch (IOException | IllegalArgumentException e) {
				result.append('?');
			}
		});
		return result.toString();
	}

	/** The texts on stickers in the language of the member who prints them. */
	private record Texts(MessageSource messages, Locale locale) {

		String get(String code, Object... arguments) {
			return messages.getMessage(code, arguments, locale);
		}

		String date(LocalDate date) {
			return DateTimeFormatter.ofPattern(get("labeling.sticker.date-pattern"), locale).format(date);
		}
	}

	private static final class Fonts {
		final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		final PDFont code = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
	}

}
