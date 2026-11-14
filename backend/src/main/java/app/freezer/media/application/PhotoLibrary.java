package app.freezer.media.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.media.MediaLibrary;
import app.freezer.media.PhotoId;
import app.freezer.media.domain.Photo;
import app.freezer.media.domain.PhotoVariant;
import app.freezer.media.domain.Photos;

/**
 * Upload and download of photos; also the module's published API.
 */
@Service
public class PhotoLibrary implements MediaLibrary {

	/** Browsers scale photos down before uploading, so real uploads are far smaller. */
	public static final int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;

	private final Photos photos;
	private final PhotoStorage storage;
	private final ImageProcessor processor;
	private final Clock clock;
	private final TransactionTemplate transaction;

	PhotoLibrary(Photos photos, PhotoStorage storage, ImageProcessor processor, Clock clock,
			PlatformTransactionManager transactions) {
		this.photos = photos;
		this.storage = storage;
		this.processor = processor;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactions);
	}

	/**
	 * Decoding and scaling take a while and need no database, so they happen before the transaction starts and don't
	 * hold a connection.
	 */
	public Photo upload(HouseholdMember member, byte[] upload) {
		if (upload.length == 0) {
			throw new InvalidImageException("media.photo.empty");
		}
		if (upload.length > MAX_UPLOAD_BYTES) {
			throw new InvalidImageException("media.photo.too-large", MAX_UPLOAD_BYTES / 1024 / 1024);
		}
		var processed = processor.process(upload);
		return transaction.execute(status -> {
			var photo = photos.save(Photo.uploaded(member.householdId(), processed.width(), processed.height(),
					member.userId(), Instant.now(clock)));
			processed.variants().forEach((variant, jpeg) -> storage.store(photo.getId(), variant, jpeg));
			return photo;
		});
	}

	@Transactional(readOnly = true)
	public byte[] load(HouseholdMember member, PhotoId photoId, PhotoVariant variant) {
		if (!contains(member.householdId(), photoId)) {
			throw new PhotoNotFoundException();
		}
		return storage.load(photoId, variant).orElseThrow(PhotoNotFoundException::new);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean contains(HouseholdId householdId, PhotoId photoId) {
		return photos.findById(photoId).filter(photo -> photo.belongsTo(householdId)).isPresent();
	}

}
