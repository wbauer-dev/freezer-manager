package app.freezer.inventory.application;

import java.time.LocalDate;

import app.freezer.catalog.ProductId;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.media.PhotoId;

/**
 * Put a new item into a freezer.
 *
 * @param gramsPerPortion optional
 * @param bestBefore optional; calculated from the product's shelf life if missing
 * @param note optional
 * @param labelCode optional: the code of a label the member already has (e.g. a reusable one on a container); a new
 *        code is generated if missing
 * @param photoId optional: a photo uploaded in the household
 */
public record FreezeCommand(ProductId productId, FreezerId freezerId, CompartmentId compartmentId, int portions,
		Integer gramsPerPortion, LocalDate frozenOn, LocalDate bestBefore, String note, String labelCode,
		PhotoId photoId) {

}
