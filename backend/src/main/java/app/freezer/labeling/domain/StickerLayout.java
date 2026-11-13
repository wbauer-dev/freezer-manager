package app.freezer.labeling.domain;

/**
 * What is printed on a sticker.
 */
public enum StickerLayout {

	/** QR code and label code only — for persistent labels, printed once. */
	QR_ONLY,

	/** QR code with the item's name and dates — for one-time labels. */
	QR_AND_CONTENT,

	/** The item's name and dates without a QR code — to show what is in a container with a persistent label. */
	CONTENT_ONLY

}
