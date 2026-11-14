package app.freezer.labeling.application;

import app.freezer.sharedkernel.LabelCode;

/**
 * A persistent label that is on no item right now.
 *
 * @param lastContents the name of what it held last, or null if it was never used
 */
public record FreeLabel(LabelCode code, String lastContents) {

}
