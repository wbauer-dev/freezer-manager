package app.freezer.media.domain;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.media.PhotoId;

/**
 * A photo uploaded in a household. Only metadata; the image data of its variants is kept by the storage adapter, so
 * loading the aggregate never loads megabytes.
 */
@Table(schema = "media", name = "photo")
public class Photo implements AggregateRoot<Photo, PhotoId> {

	@Identity
	private final PhotoId id;
	private final HouseholdId householdId;
	private final int width;
	private final int height;
	private final UserId uploadedBy;
	private final Instant uploadedAt;
	@Version
	private Long version;

	Photo(PhotoId id, HouseholdId householdId, int width, int height, UserId uploadedBy, Instant uploadedAt,
			Long version) {
		this.id = id;
		this.householdId = householdId;
		this.width = width;
		this.height = height;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = uploadedAt;
		this.version = version;
	}

	public static Photo uploaded(HouseholdId householdId, int width, int height, UserId uploadedBy, Instant now) {
		return new Photo(PhotoId.random(), householdId, width, height, uploadedBy, now, null);
	}

	public boolean belongsTo(HouseholdId householdId) {
		return this.householdId.equals(householdId);
	}

	@Override
	public PhotoId getId() {
		return id;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

}
