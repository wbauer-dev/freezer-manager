package app.freezer.media;

import app.freezer.household.HouseholdId;

/**
 * Published API of the Media module.
 */
public interface MediaLibrary {

	/** Whether the photo exists and was uploaded in this household. */
	boolean contains(HouseholdId householdId, PhotoId photoId);

}
