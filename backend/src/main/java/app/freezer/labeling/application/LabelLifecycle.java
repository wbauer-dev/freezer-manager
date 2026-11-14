package app.freezer.labeling.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.ItemConsumed;
import app.freezer.inventory.ItemDiscarded;
import app.freezer.inventory.ItemFrozen;
import app.freezer.inventory.TakeOutUndone;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.LabelSheet;
import app.freezer.labeling.domain.LabelSheets;
import app.freezer.labeling.domain.LabelingRuleViolation;
import app.freezer.labeling.domain.Labels;
import app.freezer.labeling.domain.Sticker;
import app.freezer.sharedkernel.LabelCode;

/**
 * Follows the items in the freezer: Labeling never calls Inventory, it only reacts to its events.
 * <p>
 * Each reaction runs after Inventory's transaction committed, asynchronously and in a transaction of its own. If it
 * collides with a member's change to the same label or sheet, it is applied again to the fresh data (see
 * {@link CollisionRetry}). Spring Modulith records every event publication first, so a reaction that still fails is
 * retried on restart instead of being lost. A reaction may therefore see an event again, or after a newer event of the
 * same item, and must not mind: the {@link Label} ignores an event whose item revision it has already passed.
 */
@Service
class LabelLifecycle {

	private static final Logger LOG = LoggerFactory.getLogger(LabelLifecycle.class);

	private final Labels labels;
	private final LabelSheets sheets;
	private final CollisionRetry retry;
	private final Clock clock;

	LabelLifecycle(Labels labels, LabelSheets sheets, CollisionRetry retry, Clock clock) {
		this.labels = labels;
		this.sheets = sheets;
		this.retry = retry;
		this.clock = clock;
	}

	/**
	 * A known free label (persistent, on a container) is attached; an unknown code becomes a new one-time label whose
	 * sticker — QR code with the item's name and dates — waits in the print queue.
	 */
	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ItemFrozen event) {
		var code = event.labelCode();
		if (code != null) {
			retry.run(() -> labels.findById(code).ifPresentOrElse(label -> attach(label, event),
					() -> createOneTimeLabel(code, event)));
		}
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ItemConsumed event) {
		release(event.labelCode(), event.itemId(), event.revision());
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ItemDiscarded event) {
		release(event.labelCode(), event.itemId(), event.revision());
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(TakeOutUndone event) {
		if (event.labelCode() != null) {
			retry.run(() -> labels.findById(event.labelCode()).ifPresent(label -> {
				label.reattach(event.itemId(), event.revision());
				labels.save(label);
			}));
		}
	}

	private void attach(Label label, ItemFrozen event) {
		if (!label.belongsTo(event.householdId())) {
			conflict(label.getId(), event.itemId(), "belongs to another household");
			return;
		}
		try {
			label.attach(event.itemId(), event.revision());
			labels.save(label);
		}
		catch (LabelingRuleViolation e) {
			conflict(label.getId(), event.itemId(), e.getMessage());
		}
	}

	/** The label and its sticker in the print queue are saved together, or not at all. */
	private void createOneTimeLabel(LabelCode code, ItemFrozen event) {
		labels.save(Label.oneTimeFor(code, event.householdId(), event.itemId(), event.revision(), Instant.now(clock)));
		var sheet = sheets.findById(event.householdId()).orElseGet(() -> LabelSheet.startFor(event.householdId()));
		sheet.enqueue(Sticker.qrAndContent(code, event.productName(), event.frozenOn(), event.bestBefore()));
		sheets.save(sheet);
	}

	private void release(LabelCode code, FrozenItemId itemId, long revision) {
		if (code != null) {
			retry.run(() -> labels.findById(code).ifPresent(label -> {
				label.release(itemId, revision);
				labels.save(label);
			}));
		}
	}

	/**
	 * Two packages with the same label are practically impossible — a label sits on one package. Instead of a
	 * synchronous check across contexts, the rare conflict is reported for manual resolution.
	 */
	private void conflict(LabelCode code, FrozenItemId itemId, String reason) {
		LOG.warn("Label conflict: {} cannot be attached to item {}: {}", code.formatted(), itemId, reason);
	}

}
