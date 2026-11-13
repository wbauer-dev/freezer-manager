package app.freezer.media.domain;

import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import app.freezer.media.PhotoId;

/**
 * All photos (metadata only, see {@link Photo}). Implemented by the persistence adapter.
 */
public interface Photos extends Repository<Photo, PhotoId> {

	Photo save(Photo photo);

	Optional<Photo> findById(PhotoId id);

}
