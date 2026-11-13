package app.freezer.media.domain;

/**
 * The sizes a photo is kept in.
 */
public enum PhotoVariant {

	/** For full-screen views; at most 1600 pixels on the longer side. */
	LARGE(1600),

	/** For lists and avatars; at most 400 pixels on the longer side. */
	THUMBNAIL(400);

	private final int maxSize;

	PhotoVariant(int maxSize) {
		this.maxSize = maxSize;
	}

	public int maxSize() {
		return maxSize;
	}

}
