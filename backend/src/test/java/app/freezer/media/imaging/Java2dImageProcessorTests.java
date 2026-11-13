package app.freezer.media.imaging;

import static app.freezer.media.TestImages.isJpeg;
import static app.freezer.media.TestImages.png;
import static app.freezer.media.TestImages.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

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
	void rejectsDataThatIsNoImage() {
		assertThatThrownBy(() -> processor.process("not a photo".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(InvalidImageException.class);
	}

}
