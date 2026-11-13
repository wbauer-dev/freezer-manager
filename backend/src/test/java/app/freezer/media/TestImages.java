package app.freezer.media;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Creates and reads images for tests.
 */
public final class TestImages {

	private TestImages() {
	}

	public static byte[] png(int width, int height, boolean transparent) {
		var image = new BufferedImage(width, height,
				transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(new Color(64, 98, 119, transparent ? 120 : 255));
		graphics.fillOval(0, 0, width, height);
		graphics.dispose();
		var png = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", png);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return png.toByteArray();
	}

	public static BufferedImage read(byte[] data) {
		try {
			return ImageIO.read(new ByteArrayInputStream(data));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static boolean isJpeg(byte[] data) {
		return data.length > 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8;
	}

}
