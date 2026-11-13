package app.freezer.media.persistence;

import org.springframework.data.repository.Repository;

import app.freezer.media.PhotoId;
import app.freezer.media.domain.Photo;
import app.freezer.media.domain.Photos;

interface JdbcPhotos extends Photos, Repository<Photo, PhotoId> {
}
