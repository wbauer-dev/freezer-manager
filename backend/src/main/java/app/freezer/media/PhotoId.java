package app.freezer.media;

import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

/**
 * Identifies a photo. Other modules store it to show a photo.
 */
public record PhotoId(UUID id) implements Identifier {

	public PhotoId {
		if (id == null) {
			throw new IllegalArgumentException("Photo id must not be null");
		}
	}

	public static PhotoId of(UUID id) {
		return new PhotoId(id);
	}

	public static PhotoId random() {
		return new PhotoId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return id.toString();
	}

}
