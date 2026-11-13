package app.freezer.labeling.domain;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Layout of an A4 sticker sheet, in millimetres. Positions are numbered row by row, starting at 0 top left.
 */
public record SheetFormat(int columns, int rows, double labelWidth, double labelHeight, double marginTop,
		double marginLeft, double gapX, double gapY) implements ValueObject {

	public static final double A4_WIDTH = 210;
	public static final double A4_HEIGHT = 297;

	/** 3 × 8 labels of 70 × 37 mm, the most common A4 format. Will become a household setting. */
	public static final SheetFormat DEFAULT = new SheetFormat(3, 8, 70, 37, 0.5, 0, 0, 0);

	public SheetFormat {
		if (columns < 1 || rows < 1 || labelWidth <= 0 || labelHeight <= 0) {
			throw new IllegalArgumentException("A sheet has at least one label");
		}
		if (marginLeft + columns * labelWidth + (columns - 1) * gapX > A4_WIDTH + 0.01
				|| marginTop + rows * labelHeight + (rows - 1) * gapY > A4_HEIGHT + 0.01) {
			throw new IllegalArgumentException("The labels do not fit on an A4 sheet");
		}
	}

	public int capacity() {
		return columns * rows;
	}

	public boolean contains(int position) {
		return position >= 0 && position < capacity();
	}

	/** Left edge of the label at the position, from the left of the page. */
	public double x(int position) {
		return marginLeft + (position % columns) * (labelWidth + gapX);
	}

	/** Top edge of the label at the position, from the top of the page. */
	public double y(int position) {
		return marginTop + (position / columns) * (labelHeight + gapY);
	}

}
