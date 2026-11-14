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

	/**
	 * Browsers scale photos down to 1600 pixels before uploading them. Only a browser that cannot do that sends the
	 * original, so this allows what phone cameras take by default (12 to 24 megapixels) and rejects small files that
	 * claim gigantic images.
	 */
	static final long MAX_PIXELS = 25_000_000L;

	/**
	 * Larger images are decoded with only every second, third, ... pixel, so that their longer side is at most this.
	 * That bounds the memory a decoded upload takes, and scaling down from twice the largest variant still looks
	 * smooth.
	 */
	static final int MAX_DECODED_SIZE = 2 * PhotoVariant.LARGE.maxSize();

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

	/** Every how many pixels one is decoded, in both directions. */
	static int subsampling(int width, int height) {
		return Math.ceilDiv(Math.max(width, height), MAX_DECODED_SIZE);
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
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if ((long) width * height > MAX_PIXELS) {
					throw new InvalidImageException("media.photo.too-many-pixels");
				}
				var parameters = reader.getDefaultReadParam();
				int step = subsampling(width, height);
				parameters.setSourceSubsampling(step, step, 0, 0);
				return reader.read(0, parameters);
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

	/**
	 * Scales down in halving steps, which looks much better than one big step. Never scales up. The first step draws
	 * straight from the source, without a full-size copy; the result is opaque RGB, whatever the type of the source.
	 */
	private static BufferedImage scaleDown(BufferedImage source, int maxSize) {
		double factor = Math.min(1.0, (double) maxSize / Math.max(source.getWidth(), source.getHeight()));
		int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * factor));
		int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * factor));

		var current = source;
		while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
			current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
		}
		return current.getType() == BufferedImage.TYPE_INT_RGB && current.getWidth() == targetWidth
				&& current.getHeight() == targetHeight ? current : draw(current, targetWidth, targetHeight);
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
