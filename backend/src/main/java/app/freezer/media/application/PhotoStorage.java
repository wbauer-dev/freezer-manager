package app.freezer.media.application;

import java.util.Optional;

import app.freezer.media.PhotoId;
import app.freezer.media.domain.PhotoVariant;

/**
 * Keeps the image data. Stored in PostgreSQL for now; an S3 bucket would be another implementation.
 */
public interface PhotoStorage {

	void store(PhotoId photoId, PhotoVariant variant, byte[] jpeg);

	Optional<byte[]> load(PhotoId photoId, PhotoVariant variant);

}
