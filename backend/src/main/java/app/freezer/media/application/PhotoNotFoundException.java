package app.freezer.media.application;

/**
 * The photo does not exist or belongs to another household.
 */
public class PhotoNotFoundException extends RuntimeException {

	public PhotoNotFoundException() {
		super("media.photo.not-found");
	}

}
