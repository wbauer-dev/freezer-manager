package app.freezer.media.application;

import java.util.Map;

import app.freezer.media.domain.PhotoVariant;

/**
 * Turns an uploaded file into the variants the app shows. Implementations must re-encode the image, so nothing but
 * pixels survives — in particular no metadata like the location embedded by phone cameras.
 */
public interface ImageProcessor {

	/**
	 * @throws InvalidImageException if the data is not an image that can be processed
	 */
	ProcessedPhoto process(byte[] upload);

	/** The re-encoded variants of an upload; width and height are those of the largest variant. */
	record ProcessedPhoto(int width, int height, Map<PhotoVariant, byte[]> variants) {
	}

}
