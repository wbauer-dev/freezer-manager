package app.freezer.media.persistence;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import app.freezer.media.PhotoId;
import app.freezer.media.application.PhotoStorage;
import app.freezer.media.domain.PhotoVariant;

/**
 * Keeps image data in PostgreSQL — fine for a household's photos and one container less to run.
 */
@Component
class JdbcPhotoStorage implements PhotoStorage {

	private final JdbcClient jdbc;

	JdbcPhotoStorage(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void store(PhotoId photoId, PhotoVariant variant, byte[] jpeg) {
		jdbc.sql("insert into media.photo_data (photo_id, variant, content) values (:photoId, :variant, :content)")
				.param("photoId", photoId.id())
				.param("variant", variant.name())
				.param("content", jpeg)
				.update();
	}

	@Override
	public Optional<byte[]> load(PhotoId photoId, PhotoVariant variant) {
		return jdbc.sql("select content from media.photo_data where photo_id = :photoId and variant = :variant")
				.param("photoId", photoId.id())
				.param("variant", variant.name())
				.query(byte[].class)
				.optional();
	}

}
