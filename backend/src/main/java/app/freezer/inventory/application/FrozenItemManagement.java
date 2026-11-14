package app.freezer.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.catalog.Catalog;
import app.freezer.household.HouseholdMember;
import app.freezer.inventory.DiscardReason;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.domain.BestBefore;
import app.freezer.inventory.domain.Compartment;
import app.freezer.inventory.domain.Freezer;
import app.freezer.inventory.domain.Freezers;
import app.freezer.inventory.domain.FrozenItem;
import app.freezer.inventory.domain.FrozenItems;
import app.freezer.inventory.domain.InventoryRuleViolation;
import app.freezer.inventory.domain.Portions;
import app.freezer.media.MediaLibrary;
import app.freezer.media.PhotoId;
import app.freezer.sharedkernel.LabelCode;

/**
 * Use cases around frozen items: freeze, take out, undo, discard — and the eat-first list.
 */
@Service
@Transactional
public class FrozenItemManagement {

	private final FrozenItems items;
	private final Freezers freezers;
	private final Catalog catalog;
	private final InventoryQueries queries;
	private final MediaLibrary media;
	private final Clock clock;

	FrozenItemManagement(FrozenItems items, Freezers freezers, Catalog catalog, InventoryQueries queries,
			MediaLibrary media, Clock clock) {
		this.items = items;
		this.freezers = freezers;
		this.catalog = catalog;
		this.queries = queries;
		this.media = media;
		this.clock = clock;
	}

	public FrozenItemId freeze(HouseholdMember member, FreezeCommand command) {
		var product = catalog.findProduct(member.householdId(), command.productId())
				.orElseThrow(() -> new IllegalArgumentException("inventory.product.unknown"));
		// Locked, so the compartment cannot be removed while the item goes in (see Freezers.findAndLock).
		var location = freezers.findAndLock(command.freezerId())
				.filter(freezer -> freezer.belongsTo(member.householdId()))
				.orElseThrow(() -> new IllegalArgumentException("inventory.freezer.unknown"))
				.locationOf(command.compartmentId());
		var bestBefore = command.bestBefore() == null
				? BestBefore.calculate(command.frozenOn(), product.shelfLife())
				: BestBefore.overriddenWith(command.bestBefore());

		var labelCode = command.labelCode() == null || command.labelCode().isBlank()
				? LabelCode.generate()
				: LabelCode.parse(command.labelCode());
		// A label sits on one package at a time. This concerns all items of the household, so it is checked here.
		if (items.labelInUse(member.householdId(), labelCode)) {
			throw new InventoryRuleViolation("inventory.label.in-use", labelCode.formatted());
		}

		var item = FrozenItem.freeze(member.householdId(), location, product.id(), product.name(), labelCode,
				Portions.of(command.portions()), command.gramsPerPortion(), command.frozenOn(), bestBefore,
				command.note(), member.userId());
		if (command.photoId() != null) {
			item.attachPhoto(photoOf(member, command.photoId()));
		}
		return items.save(item).getId();
	}

	public void attachPhoto(HouseholdMember member, FrozenItemId itemId, PhotoId photoId) {
		var item = itemOf(member, itemId);
		item.attachPhoto(photoOf(member, photoId));
		items.save(item);
	}

	public void takeOut(HouseholdMember member, FrozenItemId itemId, int portions) {
		var item = itemOf(member, itemId);
		item.takeOut(portions, member.userId(), member.displayName(), now());
		items.save(item);
	}

	public void undoTakeOut(HouseholdMember member, FrozenItemId itemId) {
		var item = itemOf(member, itemId);
		// Locked like when freezing: the undo may bring the item back into its compartment.
		var freezer = freezers.findAndLock(item.getLocation().freezerId()).orElseThrow();
		item.undoTakeOut(member.userId(), now(), freezer, labelOnAnotherItem(item));
		items.save(item);
	}

	public void discard(HouseholdMember member, FrozenItemId itemId, DiscardReason reason) {
		var item = itemOf(member, itemId);
		item.discard(reason, now());
		items.save(item);
	}

	@Transactional(readOnly = true)
	public ItemDetails details(HouseholdMember member, FrozenItemId itemId) {
		var item = itemOf(member, itemId);
		var freezer = freezerOf(item);
		var compartmentName = freezer.compartment(item.getLocation().compartmentId())
				.map(Compartment::name)
				.orElse(null);
		return new ItemDetails(item, freezer.getName(), compartmentName, item.expiryStatusOn(today()),
				item.canUndoTakeOut(member.userId(), now(), freezer, labelOnAnotherItem(item)));
	}

	@Transactional(readOnly = true)
	public List<EatFirstEntry> eatFirst(HouseholdMember member) {
		return queries.eatFirst(member.householdId(), today());
	}

	private FrozenItem itemOf(HouseholdMember member, FrozenItemId itemId) {
		return items.findById(itemId)
				.filter(item -> item.belongsTo(member.householdId()))
				.orElseThrow(() -> new InventoryNotFoundException("inventory.item.not-found"));
	}

	/** The freezer the item is or was stored in. It is kept even when removed, so it always exists. */
	private Freezer freezerOf(FrozenItem item) {
		return freezers.findById(item.getLocation().freezerId()).orElseThrow();
	}

	/**
	 * Whether another item in the household's freezers carries this item's label. That is only possible while this
	 * item is out of the freezer, e.g. after its last portion was taken out: the label may have moved on since. Like
	 * when freezing, this concerns all items of the household, so it is looked up here.
	 */
	private boolean labelOnAnotherItem(FrozenItem item) {
		return !item.isInFreezer() && item.getLabelCode()
				.map(label -> items.labelInUse(item.getHouseholdId(), label))
				.orElse(false);
	}

	private PhotoId photoOf(HouseholdMember member, PhotoId photoId) {
		if (!media.contains(member.householdId(), photoId)) {
			throw new IllegalArgumentException("inventory.photo.unknown");
		}
		return photoId;
	}

	private Instant now() {
		return Instant.now(clock);
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

}
