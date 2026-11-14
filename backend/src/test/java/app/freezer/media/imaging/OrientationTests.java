package app.freezer.media.imaging;

import static app.freezer.media.TestImages.png;
import static app.freezer.media.TestImages.read;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import app.freezer.media.domain.PhotoVariant;

class OrientationTests {

	static final Color RED = new Color(220, 30, 30);
	static final Color BLUE = new Color(30, 30, 220);

	@ParameterizedTest
	@EnumSource(Orientation.class)
	void readsTheExifOrientationOfAJpegInBothByteOrders(Orientation orientation) {
		var exifValue = orientation.ordinal() + 1;

		assertThat(Orientation.of(withOrientation(halves(), exifValue, ByteOrder.BIG_ENDIAN))).isEqualTo(orientation);
		assertThat(Orientation.of(withOrientation(halves(), exifValue, ByteOrder.LITTLE_ENDIAN)))
				.isEqualTo(orientation);
	}

	@Test
	void takesAnythingWithoutAnOrientationAsUpright() {
		var jpeg = halves();

		assertThat(Orientation.of(jpeg)).isEqualTo(Orientation.UPRIGHT);
		assertThat(Orientation.of(png(10, 10, false))).isEqualTo(Orientation.UPRIGHT);
		assertThat(Orientation.of(new byte[0])).isEqualTo(Orientation.UPRIGHT);
		assertThat(Orientation.of(withOrientation(jpeg, 9, ByteOrder.BIG_ENDIAN))).isEqualTo(Orientation.UPRIGHT);
		var withExif = withOrientation(jpeg, 6, ByteOrder.BIG_ENDIAN);
		assertThat(Orientation.of(Arrays.copyOf(withExif, 30))).isEqualTo(Orientation.UPRIGHT);
	}

	/** The stored image is 1 2 3 / 4 5 6; each orientation says how it lies compared with what was photographed. */
	@Test
	void turnsEveryOrientationUpright() {
		assertThat(upright(Orientation.UPRIGHT)).isEqualTo(new int[][] { { 1, 2, 3 }, { 4, 5, 6 } });
		assertThat(upright(Orientation.MIRRORED)).isEqualTo(new int[][] { { 3, 2, 1 }, { 6, 5, 4 } });
		assertThat(upright(Orientation.UPSIDE_DOWN)).isEqualTo(new int[][] { { 6, 5, 4 }, { 3, 2, 1 } });
		assertThat(upright(Orientation.FLIPPED)).isEqualTo(new int[][] { { 4, 5, 6 }, { 1, 2, 3 } });
		assertThat(upright(Orientation.TRANSPOSED)).isEqualTo(new int[][] { { 1, 4 }, { 2, 5 }, { 3, 6 } });
		assertThat(upright(Orientation.ROTATED_COUNTERCLOCKWISE))
				.isEqualTo(new int[][] { { 4, 1 }, { 5, 2 }, { 6, 3 } });
		assertThat(upright(Orientation.TRANSVERSED)).isEqualTo(new int[][] { { 6, 3 }, { 5, 2 }, { 4, 1 } });
		assertThat(upright(Orientation.ROTATED_CLOCKWISE)).isEqualTo(new int[][] { { 3, 6 }, { 2, 5 }, { 1, 4 } });
	}

	/** A phone held upright stores a landscape image and value 6: its top is on the right. */
	@Test
	void uploadedJpegsAreStoredTheRightWayUp() {
		var processor = new Java2dImageProcessor();

		var portrait = processor.process(withOrientation(halves(), 6, ByteOrder.BIG_ENDIAN));
		var large = read(portrait.variants().get(PhotoVariant.LARGE));
		assertThat(portrait.width()).isEqualTo(200);
		assertThat(portrait.height()).isEqualTo(400);
		assertThat(large.getWidth()).isEqualTo(200);
		assertThat(colorAt(large, 100, 50)).isEqualTo(RED);
		assertThat(colorAt(large, 100, 350)).isEqualTo(BLUE);
		var thumbnail = read(portrait.variants().get(PhotoVariant.THUMBNAIL));
		assertThat(thumbnail.getHeight()).isEqualTo(400);

		var turnedTheOtherWay = read(processor.process(withOrientation(halves(), 8, ByteOrder.LITTLE_ENDIAN))
				.variants().get(PhotoVariant.LARGE));
		assertThat(colorAt(turnedTheOtherWay, 100, 50)).isEqualTo(BLUE);
		assertThat(colorAt(turnedTheOtherWay, 100, 350)).isEqualTo(RED);
	}

	private static int[][] upright(Orientation orientation) {
		var stored = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
		stored.setRGB(0, 0, 3, 2, new int[] { 1, 2, 3, 4, 5, 6 }, 0, 3);
		var upright = orientation.turnUpright(stored);
		var pixels = new int[upright.getHeight()][upright.getWidth()];
		for (int y = 0; y < upright.getHeight(); y++) {
			for (int x = 0; x < upright.getWidth(); x++) {
				pixels[y][x] = upright.getRGB(x, y) & 0xff_ffff;
			}
		}
		return pixels;
	}

	/** A JPEG of 400 × 200 pixels: the left half red, the right half blue. */
	private static byte[] halves() {
		var image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(RED);
		graphics.fillRect(0, 0, 200, 200);
		graphics.setColor(BLUE);
		graphics.fillRect(200, 0, 200, 200);
		graphics.dispose();
		var jpeg = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "jpeg", jpeg);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return jpeg.toByteArray();
	}

	/**
	 * Adds an Exif segment as cameras write it: after the JFIF header, with the camera make first and then the
	 * orientation.
	 */
	private static byte[] withOrientation(byte[] jpeg, int value, ByteOrder order) {
		var tiff = ByteBuffer.allocate(8 + 2 + 2 * 12 + 4).order(order)
				.put((order == ByteOrder.LITTLE_ENDIAN ? "II" : "MM").getBytes(StandardCharsets.US_ASCII))
				.putShort((short) 42).putInt(8)
				.putShort((short) 2)
				.putShort((short) 0x010f).putShort((short) 2).putInt(4).put("Cam\0".getBytes(StandardCharsets.US_ASCII))
				.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) value).putShort((short) 0)
				.putInt(0)
				.array();
		var exif = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
		int afterJfif = 4 + ((jpeg[4] & 0xff) << 8 | jpeg[5] & 0xff);
		return ByteBuffer.allocate(jpeg.length + 4 + exif.length + tiff.length)
				.put(jpeg, 0, afterJfif)
				.put(new byte[] { (byte) 0xff, (byte) 0xe1 }).putShort((short) (2 + exif.length + tiff.length))
				.put(exif).put(tiff)
				.put(jpeg, afterJfif, jpeg.length - afterJfif)
				.array();
	}

	/** Snaps a JPEG's colour to the closest of the two used, as compression shifts colours slightly. */
	private static Color colorAt(BufferedImage image, int x, int y) {
		var color = new Color(image.getRGB(x, y));
		return color.getRed() > color.getBlue() ? RED : BLUE;
	}

}
