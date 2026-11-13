package app.freezer.media.imaging;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

import org.springframework.stereotype.Component;

import app.freezer.media.application.ImageProcessor;
import app.freezer.media.application.InvalidImageException;
import app.freezer.media.domain.PhotoVariant;

/**
 * Decodes the upload, scales it to each variant and writes plain JPEGs. Re-encoding drops all metadata, including
 * the location phone cameras embed.
 */
@Component
class Java2dImageProcessor implements ImageProcessor {

	/** Guards against tiny files that decode into gigantic images. */
	private static final long MAX_PIXELS = 50_000_000L;
	private static final float JPEG_QUALITY = 0.85f;

	@Override
	public ProcessedPhoto process(byte[] upload) {
		var image = read(upload);
		var variants = new EnumMap<PhotoVariant, byte[]>(PhotoVariant.class);
		var large = scaleDown(image, PhotoVariant.LARGE.maxSize());
		variants.put(PhotoVariant.LARGE, jpeg(large));
		variants.put(PhotoVariant.THUMBNAIL, jpeg(scaleDown(large, PhotoVariant.THUMBNAIL.maxSize())));
		return new ProcessedPhoto(large.getWidth(), large.getHeight(), variants);
	}

	private static BufferedImage read(byte[] upload) {
		try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
			var readers = input == null ? null : ImageIO.getImageReaders(input);
			if (readers == null || !readers.hasNext()) {
				throw new InvalidImageException("media.photo.unsupported");
			}
			var reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw new InvalidImageException("media.photo.too-many-pixels");
				}
				return reader.read(0);
			}
			finally {
				reader.dispose();
			}
		}
		catch (InvalidImageException e) {
			throw e;
		}
		catch (IOException | RuntimeException e) {
			var unreadable = new InvalidImageException("media.photo.unreadable");
			unreadable.initCause(e);
			throw unreadable;
		}
	}

	/** Scales down in halving steps, which looks much better than one big step. Never scales up. */
	private static BufferedImage scaleDown(BufferedImage source, int maxSize) {
		double factor = Math.min(1.0, (double) maxSize / Math.max(source.getWidth(), source.getHeight()));
		int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * factor));
		int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * factor));

		var current = draw(source, source.getWidth(), source.getHeight());
		while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
			current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return current.getWidth() == targetWidth && current.getHeight() == targetHeight
				? current
				: draw(current, targetWidth, targetHeight);
	}

	/** Draws onto an opaque RGB canvas; transparent areas become white. */
	private static BufferedImage draw(BufferedImage source, int width, int height) {
		var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

	private static byte[] jpeg(BufferedImage image) {
		var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		var parameters = writer.getDefaultWriteParam();
		parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		parameters.setCompressionQuality(JPEG_QUALITY);
		var jpeg = new ByteArrayOutputStream();
		try (var output = ImageIO.createImageOutputStream(jpeg)) {
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), parameters);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		finally {
			writer.dispose();
		}
		return jpeg.toByteArray();
	}

}
