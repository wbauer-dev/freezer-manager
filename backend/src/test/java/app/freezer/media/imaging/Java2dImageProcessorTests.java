package app.freezer.media.imaging;

import static app.freezer.media.TestImages.isJpeg;
import static app.freezer.media.TestImages.png;
import static app.freezer.media.TestImages.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import app.freezer.media.application.InvalidImageException;
import app.freezer.media.domain.PhotoVariant;

class Java2dImageProcessorTests {

	Java2dImageProcessor processor = new Java2dImageProcessor();

	@Test
	void scalesLargePhotosDownToEachVariant() {
		var photo = processor.process(png(3000, 2000, false));

		assertThat(photo.width()).isEqualTo(1600);
		assertThat(photo.height()).isEqualTo(1067);
		var large = photo.variants().get(PhotoVariant.LARGE);
		var thumbnail = photo.variants().get(PhotoVariant.THUMBNAIL);
		assertThat(isJpeg(large)).isTrue();
		assertThat(read(large).getWidth()).isEqualTo(1600);
		assertThat(read(thumbnail).getWidth()).isEqualTo(400);
		assertThat(read(thumbnail).getHeight()).isEqualTo(267);
	}

	@Test
	void neverScalesUp() {
		var photo = processor.process(png(300, 200, false));

		assertThat(read(photo.variants().get(PhotoVariant.LARGE)).getWidth()).isEqualTo(300);
		assertThat(read(photo.variants().get(PhotoVariant.THUMBNAIL)).getWidth()).isEqualTo(300);
	}

	@Test
	void turnsTransparentImagesIntoJpegs() {
		var photo = processor.process(png(800, 800, true));

		assertThat(isJpeg(photo.variants().get(PhotoVariant.THUMBNAIL))).isTrue();
	}

	@Test
	void decodesLargePhotosWithOnlyEveryNthPixel() throws IOException {
		assertThat(Java2dImageProcessor.subsampling(3200, 2400)).isEqualTo(1);
		assertThat(Java2dImageProcessor.subsampling(4032, 3024)).isEqualTo(2);
		assertThat(Java2dImageProcessor.subsampling(100, 9601)).isEqualTo(4);

		var gray = new BufferedImage(4000, 3000, BufferedImage.TYPE_BYTE_GRAY);
		var png = new ByteArrayOutputStream();
		ImageIO.write(gray, "png", png);
		var photo = processor.process(png.toByteArray());

		assertThat(photo.width()).isEqualTo(1600);
		assertThat(photo.height()).isEqualTo(1200);
	}

	@Test
	void rejectsSmallFilesThatClaimGiganticImages() {
		// 7071 × 7071 pixels with 16 bits per channel and alpha would take 400 MB once decoded.
		assertThatThrownBy(() -> processor.process(pngHeader(7071, 7071)))
				.isInstanceOf(InvalidImageException.class).hasMessage("media.photo.too-many-pixels");
		assertThat(5000L * 5000).isLessThanOrEqualTo(Java2dImageProcessor.MAX_PIXELS);
		assertThatThrownBy(() -> processor.process(pngHeader(5000, 5001)))
				.hasMessage("media.photo.too-many-pixels");
	}

	@Test
	void rejectsDataThatIsNoImage() {
		assertThatThrownBy(() -> processor.process("not a photo".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(InvalidImageException.class);
	}

	/** Only the start of a PNG: a few bytes that claim an image of the given size, 16 bits per channel with alpha. */
	private static byte[] pngHeader(int width, int height) {
		var header = ByteBuffer.allocate(17).put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(width)
				.putInt(height).put(new byte[] { 16, 6, 0, 0, 0 }).array();
		var crc = new CRC32();
		crc.update(header);
		return ByteBuffer.allocate(33).put(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' })
				.putInt(13).put(header).putInt((int) crc.getValue()).array();
	}

}
