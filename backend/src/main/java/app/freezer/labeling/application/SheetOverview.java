package app.freezer.labeling.application;

import java.util.List;
import java.util.Set;

import app.freezer.labeling.domain.SheetFormat;
import app.freezer.labeling.domain.Sticker;

/**
 * The current sheet and the stickers waiting to be printed.
 */
public record SheetOverview(SheetFormat format, Set<Integer> usedPositions, int nextPosition, List<Sticker> queue) {

}
