package app.freezer.media.imaging;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * How the stored pixels of a photo lie compared with what was photographed. Phone cameras store the pixels as the
 * sensor reads them and note the orientation in the Exif metadata (tag 0x0112) instead of turning the image.
 * Browsers turn photos upright before uploading; one that cannot do that sends the original, so the server reads the
 * tag of JPEGs, the format phones use.
 * <p>
 * The constants are in the order of the Exif values 1 to 8.
 */
enum Orientation {

	UPRIGHT, MIRRORED, UPSIDE_DOWN, FLIPPED, TRANSPOSED, ROTATED_COUNTERCLOCKWISE, TRANSVERSED, ROTATED_CLOCKWISE;

	private static final int MARKER = 0xff;
	private static final int START_OF_IMAGE = 0xd8;
	private static final int START_OF_SCAN = 0xda;
	private static final int END_OF_IMAGE = 0xd9;
	private static final int APP1 = 0xe1;
	private static final byte[] EXIF = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
	private static final int TIFF_MAGIC = 42;
	private static final int ORIENTATION_TAG = 0x0112;
	private static final int SHORT = 3;
	private static final int DIRECTORY_ENTRY_SIZE = 12;

	/**
	 * The orientation noted in a JPEG's Exif metadata. Anything else — another format, no Exif, a damaged segment — is
	 * taken as upright, so the photo is kept as stored.
	 */
	static Orientation of(byte[] upload) {
		try {
			return exifOf(upload).map(Orientation::inTiff).orElse(UPRIGHT);
		}
		catch (IndexOutOfBoundsException e) {
			return UPRIGHT;
		}
	}

	/** Turns a decoded image the right way up; returns the image itself if it is upright already. */
	BufferedImage turnUpright(BufferedImage image) {
		if (this == UPRIGHT) {
			return image;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		boolean swapsSides = ordinal() >= TRANSPOSED.ordinal();
		int targetWidth = swapsSides ? height : width;
		int targetHeight = swapsSides ? width : height;
		var source = image.getRGB(0, 0, width, height, null, 0, width);
		var target = new int[source.length];
		for (int y = 0; y < targetHeight; y++) {
			for (int x = 0; x < targetWidth; x++) {
				target[y * targetWidth + x] = source[sourceIndex(x, y, width, height)];
			}
		}
		var upright = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		upright.setRGB(0, 0, targetWidth, targetHeight, target, 0, targetWidth);
		return upright;
	}

	/** Where the pixel at (x, y) of the upright image is in the stored one, which is width × height. */
	private int sourceIndex(int x, int y, int width, int height) {
		return switch (this) {
			case UPRIGHT -> y * width + x;
			case MIRRORED -> y * width + width - 1 - x;
			case UPSIDE_DOWN -> (height - 1 - y) * width + width - 1 - x;
			case FLIPPED -> (height - 1 - y) * width + x;
			case TRANSPOSED -> x * width + y;
			case ROTATED_COUNTERCLOCKWISE -> (height - 1 - x) * width + y;
			case TRANSVERSED -> (height - 1 - x) * width + width - 1 - y;
			case ROTATED_CLOCKWISE -> x * width + width - 1 - y;
		};
	}

	/**
	 * The Exif data (a TIFF structure) of a JPEG: walks the segments before the image data, each a marker and its
	 * length, until it finds an APP1 segment that starts with "Exif".
	 */
	private static Optional<ByteBuffer> exifOf(byte[] data) {
		var jpeg = ByteBuffer.wrap(data);
		if (u8(jpeg, 0) != MARKER || u8(jpeg, 1) != START_OF_IMAGE) {
			return Optional.empty();
		}
		int offset = 2;
		int marker = u8(jpeg, offset + 1);
		while (u8(jpeg, offset) == MARKER && marker != START_OF_SCAN && marker != END_OF_IMAGE) {
			if (marker == MARKER) {
				offset++; // a fill byte before the marker
			}
			else {
				int length = u16(jpeg, offset + 2);
				if (marker == APP1 && isExif(data, offset + 4)) {
					return Optional.of(ByteBuffer.wrap(data, offset + 4 + EXIF.length, length - 2 - EXIF.length)
							.slice());
				}
				offset += 2 + length;
			}
			marker = u8(jpeg, offset + 1);
		}
		return Optional.empty();
	}

	/** Looks for the orientation in the first image file directory (IFD0) of the Exif data. */
	private static Orientation inTiff(ByteBuffer tiff) {
		if (tiff.get(0) == 'I' && tiff.get(1) == 'I') {
			tiff.order(ByteOrder.LITTLE_ENDIAN);
		}
		else if (tiff.get(0) != 'M' || tiff.get(1) != 'M') {
			return UPRIGHT;
		}
		if (u16(tiff, 2) != TIFF_MAGIC) {
			return UPRIGHT;
		}
		int directory = tiff.getInt(4);
		int entries = u16(tiff, directory);
		for (int i = 0; i < entries; i++) {
			int entry = directory + 2 + i * DIRECTORY_ENTRY_SIZE;
			if (u16(tiff, entry) == ORIENTATION_TAG) {
				int value = u16(tiff, entry + 8);
				boolean valid = u16(tiff, entry + 2) == SHORT && value >= 1 && value <= values().length;
				return valid ? values()[value - 1] : UPRIGHT;
			}
		}
		return UPRIGHT;
	}

	private static boolean isExif(byte[] data, int offset) {
		return offset + EXIF.length <= data.length
				&& Arrays.equals(data, offset, offset + EXIF.length, EXIF, 0, EXIF.length);
	}

	private static int u8(ByteBuffer buffer, int index) {
		return Byte.toUnsignedInt(buffer.get(index));
	}

	private static int u16(ByteBuffer buffer, int index) {
		return Short.toUnsignedInt(buffer.getShort(index));
	}

}
