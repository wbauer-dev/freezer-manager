package app.freezer.labeling.domain;

/**
 * Where a label is in its life; see {@link Label} for the transitions.
 */
public enum LabelState {
	FREE, ATTACHED, USED_UP
}
