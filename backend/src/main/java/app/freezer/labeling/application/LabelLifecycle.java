package app.freezer.labeling.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

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
 * Each reaction runs after Inventory's transaction committed, asynchronously and in a transaction of its own. Spring
 * Modulith records every event publication first, so a reaction that fails is retried on restart instead of being
 * lost.
 */
@Service
class LabelLifecycle {

	private static final Logger LOG = LoggerFactory.getLogger(LabelLifecycle.class);

	private final Labels labels;
	private final LabelSheets sheets;
	private final Clock clock;

	LabelLifecycle(Labels labels, LabelSheets sheets, Clock clock) {
		this.labels = labels;
		this.sheets = sheets;
		this.clock = clock;
	}

	/**
	 * A known free label (persistent, on a container) is attached; an unknown code becomes a new one-time label whose
	 * sticker — QR code with the item's name and dates — waits in the print queue.
	 */
	@ApplicationModuleListener
	void on(ItemFrozen event) {
		var code = event.labelCode();
		if (code == null) {
			return;
		}
		labels.findById(code).ifPresentOrElse(label -> {
			if (!label.belongsTo(event.householdId())) {
				conflict(code, event.itemId(), "belongs to another household");
				return;
			}
			try {
				label.attach(event.itemId());
				labels.save(label);
			}
			catch (LabelingRuleViolation e) {
				conflict(code, event.itemId(), e.getMessage());
			}
		}, () -> {
			labels.save(Label.oneTimeFor(code, event.householdId(), event.itemId(), Instant.now(clock)));
			var sheet = sheets.findById(event.householdId()).orElseGet(() -> LabelSheet.startFor(event.householdId()));
			sheet.enqueue(Sticker.qrAndContent(code, event.productName(), event.frozenOn(), event.bestBefore()));
			sheets.save(sheet);
		});
	}

	@ApplicationModuleListener
	void on(ItemConsumed event) {
		release(event.labelCode(), event.itemId());
	}

	@ApplicationModuleListener
	void on(ItemDiscarded event) {
		release(event.labelCode(), event.itemId());
	}

	@ApplicationModuleListener
	void on(TakeOutUndone event) {
		if (event.labelCode() != null) {
			labels.findById(event.labelCode()).ifPresent(label -> {
				label.reattach(event.itemId());
				labels.save(label);
			});
		}
	}

	private void release(LabelCode code, FrozenItemId itemId) {
		if (code != null) {
			labels.findById(code).ifPresent(label -> {
				label.release(itemId);
				labels.save(label);
			});
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
