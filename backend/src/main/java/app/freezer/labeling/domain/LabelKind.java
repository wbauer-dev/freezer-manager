package app.freezer.labeling.domain;

/**
 * How a label is used: for one item only, or again and again on the same container.
 */
public enum LabelKind {

	/** Printed for one item, e.g. on a freezer bag; used up when the item is eaten or discarded. */
	ONE_TIME,

	/** Stays on a container, lid or bag clip; free again for the next filling when the item is eaten or discarded. */
	PERSISTENT

}
