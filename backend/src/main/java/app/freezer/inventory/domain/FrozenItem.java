package app.freezer.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.event.types.DomainEvent;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.inventory.DiscardReason;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.ItemConsumed;
import app.freezer.inventory.ItemDiscarded;
import app.freezer.inventory.ItemFrozen;
import app.freezer.inventory.PortionsTakenOut;
import app.freezer.inventory.TakeOutUndone;
import app.freezer.media.PhotoId;
import app.freezer.sharedkernel.InvalidValueException;
import app.freezer.sharedkernel.LabelCode;

/**
 * One physical package or container in a freezer.
 * <p>
 * Rules:
 * <ul>
 * <li>Portions can only be taken out while the item is in the freezer, and never more than are left.</li>
 * <li>No refreezing: taken-out portions cannot be put back. Only the member who took them out may undo their latest
 * take-out within {@link #UNDO_PERIOD} — for mistakes such as scanning the wrong package. An item that the take-out
 * consumed only comes back while its compartment still exists and its label is not on another item.</li>
 * <li>Taking out the last portion consumes the item. Consumed and discarded items cannot be changed.</li>
 * <li>The best-before date is not before the freezing date.</li>
 * <li>Every item carries a label code. Within a household, a code is on at most one item in the freezer (checked by the
 * application service, backed by a unique index).</li>
 * </ul>
 * Changes are announced with domain events, published when the item is saved.
 */
@Table(schema = "inventory", name = "frozen_item")
public class FrozenItem implements AggregateRoot<FrozenItem, FrozenItemId> {

	public static final Duration UNDO_PERIOD = Duration.ofMinutes(10);
	public static final int MAX_NOTE_LENGTH = 200;
	public static final int MAX_GRAMS_PER_PORTION = 20_000;

	@Identity
	private final FrozenItemId id;
	private final HouseholdId householdId;
	@Embedded.Nullable
	private final Location location;
	private final ProductId productId;
	/** The product's name, kept even if the product is removed from the catalog. */
	private String productName;
	private final String note;
	/** {@code null} for items frozen before labels existed. */
	private final LabelCode labelCode;
	private PhotoId photoId;
	@Embedded.Nullable(prefix = "portions_")
	private Portions portions;
	private final Integer gramsPerPortion;
	private final LocalDate frozenOn;
	@Embedded.Nullable(prefix = "best_before_")
	private final BestBefore bestBefore;
	private final UserId frozenBy;
	private ItemState state;
	private DiscardReason discardReason;
	private Instant removedAt;
	@MappedCollection(idColumn = "frozen_item_id", keyColumn = "position")
	private final List<Withdrawal> withdrawals;
	@Version
	private Long version;

	@Transient
	private final List<DomainEvent> events = new ArrayList<>();

	FrozenItem(FrozenItemId id, HouseholdId householdId, Location location, ProductId productId, String productName,
			String note, LabelCode labelCode, PhotoId photoId, Portions portions, Integer gramsPerPortion,
			LocalDate frozenOn, BestBefore bestBefore, UserId frozenBy, ItemState state, DiscardReason discardReason,
			Instant removedAt, List<Withdrawal> withdrawals, Long version) {
		this.id = id;
		this.householdId = householdId;
		this.location = location;
		this.productId = productId;
		this.productName = productName;
		this.note = note;
		this.labelCode = labelCode;
		this.photoId = photoId;
		this.portions = portions;
		this.gramsPerPortion = gramsPerPortion;
		this.frozenOn = frozenOn;
		this.bestBefore = bestBefore;
		this.frozenBy = frozenBy;
		this.state = state;
		this.discardReason = discardReason;
		this.removedAt = removedAt;
		this.withdrawals = new ArrayList<>(withdrawals);
		this.version = version;
	}

	/**
	 * Puts a new item into a freezer.
	 */
	public static FrozenItem freeze(HouseholdId householdId, Location location, ProductId productId, String productName,
			LabelCode labelCode, Portions portions, Integer gramsPerPortion, LocalDate frozenOn, BestBefore bestBefore,
			String note, UserId frozenBy) {
		if (labelCode == null) {
			throw new IllegalArgumentException("A frozen item needs a label code");
		}
		if (productName == null || productName.isBlank()) {
			throw new IllegalArgumentException("Product name must not be blank");
		}
		if (frozenOn == null) {
			throw new IllegalArgumentException("Freezing date must not be null");
		}
		if (bestBefore.date().isBefore(frozenOn)) {
			throw new IllegalArgumentException("inventory.item.best-before-too-early");
		}
		if (gramsPerPortion != null && (gramsPerPortion < 1 || gramsPerPortion > MAX_GRAMS_PER_PORTION)) {
			throw new InvalidValueException("inventory.item.weight-invalid", MAX_GRAMS_PER_PORTION);
		}

		var item = new FrozenItem(FrozenItemId.random(), householdId, location, productId, productName, validNote(note),
				labelCode, null, portions, gramsPerPortion, frozenOn, bestBefore, frozenBy, ItemState.IN_FREEZER, null,
				null, List.of(), null);
		item.events.add(new ItemFrozen(item.id, householdId, productId, productName, portions.initial(), frozenOn,
				bestBefore.date(), labelCode));
		return item;
	}

	public void takeOut(int count, UserId by, String byName, Instant now) {
		requireInFreezer();
		portions = portions.takeOut(count);
		withdrawals.add(new Withdrawal(count, by, byName, now));
		events.add(new PortionsTakenOut(id, householdId, productId, count, portions.remaining(), labelCode));

		if (portions.isEmpty()) {
			state = ItemState.CONSUMED;
			removedAt = now;
			events.add(new ItemConsumed(id, householdId, productId, labelCode));
		}
	}

	/**
	 * Whether the member can undo the latest take-out now. Same arguments as {@link #undoTakeOut}.
	 */
	public boolean canUndoTakeOut(UserId by, Instant now, Freezer freezer, boolean labelOnAnotherItem) {
		return reasonUndoIsImpossible(by, now, freezer, labelOnAnotherItem).isEmpty();
	}

	/**
	 * Undoes the latest take-out. If it had consumed the item, the item is back in the freezer: in its compartment,
	 * which must still exist, and with its label, which may have been put on another item in the meantime. Both concern
	 * other aggregates, so the caller passes the freezer the item was stored in and whether another item in the freezer
	 * carries this item's label.
	 */
	public void undoTakeOut(UserId by, Instant now, Freezer freezer, boolean labelOnAnotherItem) {
		reasonUndoIsImpossible(by, now, freezer, labelOnAnotherItem).ifPresent(reason -> {
			throw reason;
		});

		var latest = withdrawals.removeLast();
		portions = portions.putBack(latest.portions());
		if (state == ItemState.CONSUMED) {
			state = ItemState.IN_FREEZER;
			removedAt = null;
		}
		events.add(new TakeOutUndone(id, householdId, productId, latest.portions(), portions.remaining(), labelCode));
	}

	public void discard(DiscardReason reason, Instant now) {
		if (reason == null) {
			throw new IllegalArgumentException("inventory.item.discard-reason-missing");
		}
		requireInFreezer();
		state = ItemState.DISCARDED;
		discardReason = reason;
		removedAt = now;
		events.add(new ItemDiscarded(id, householdId, productId, reason, labelCode));
	}

	/**
	 * Shows the food with a photo (replacing an earlier one). The caller makes sure the photo belongs to the household.
	 */
	public void attachPhoto(PhotoId photo) {
		if (photo == null) {
			throw new IllegalArgumentException("Photo must not be null");
		}
		requireInFreezer();
		this.photoId = photo;
	}

	/** Follows a renamed product while the item is in the freezer; afterwards its history stays as it was. */
	public void productRenamed(String newName) {
		if (isInFreezer() && newName != null && !newName.isBlank()) {
			this.productName = newName;
		}
	}

	public ExpiryStatus expiryStatusOn(LocalDate today) {
		return bestBefore.statusOn(today);
	}

	public boolean isInFreezer() {
		return state == ItemState.IN_FREEZER;
	}

	public boolean belongsTo(HouseholdId householdId) {
		return this.householdId.equals(householdId);
	}

	private void requireInFreezer() {
		if (!isInFreezer()) {
			throw new InventoryRuleViolation("inventory.item.not-in-freezer");
		}
	}

	private Optional<InventoryRuleViolation> reasonUndoIsImpossible(UserId by, Instant now, Freezer freezer,
			boolean labelOnAnotherItem) {
		if (state == ItemState.DISCARDED) {
			return Optional.of(new InventoryRuleViolation("inventory.item.discarded"));
		}
		if (withdrawals.isEmpty()) {
			return Optional.of(new InventoryRuleViolation("inventory.take-out.nothing-to-undo"));
		}
		var latest = withdrawals.getLast();
		if (!latest.takenBy().equals(by)) {
			return Optional.of(new InventoryRuleViolation("inventory.take-out.undo-by-other", latest.takenByName()));
		}
		if (now.isAfter(latest.takenAt().plus(UNDO_PERIOD))) {
			return Optional.of(new InventoryRuleViolation("inventory.take-out.undo-expired", UNDO_PERIOD.toMinutes()));
		}
		if (state == ItemState.CONSUMED) {
			// The item would come back. Since it left, its place may have been removed — the item would be lost there —
			// and its label may have been put on another package.
			if (!freezer.canStore(location)) {
				return Optional.of(new InventoryRuleViolation("inventory.take-out.undo-place-removed"));
			}
			if (labelOnAnotherItem && labelCode != null) {
				return Optional.of(new InventoryRuleViolation("inventory.label.in-use", labelCode.formatted()));
			}
		}
		return Optional.empty();
	}

	private static String validNote(String note) {
		if (note == null || note.isBlank()) {
			return null;
		}
		var trimmed = note.strip();
		if (trimmed.length() > MAX_NOTE_LENGTH) {
			throw new InvalidValueException("inventory.item.note-too-long", MAX_NOTE_LENGTH);
		}
		return trimmed;
	}

	@DomainEvents
	Collection<DomainEvent> domainEvents() {
		return List.copyOf(events);
	}

	@AfterDomainEventPublication
	void clearDomainEvents() {
		events.clear();
	}

	@Override
	public FrozenItemId getId() {
		return id;
	}

	public HouseholdId getHouseholdId() {
		return householdId;
	}

	public Location getLocation() {
		return location;
	}

	public ProductId getProductId() {
		return productId;
	}

	public String getProductName() {
		return productName;
	}

	public Optional<String> getNote() {
		return Optional.ofNullable(note);
	}

	public Optional<LabelCode> getLabelCode() {
		return Optional.ofNullable(labelCode);
	}

	public Optional<PhotoId> getPhotoId() {
		return Optional.ofNullable(photoId);
	}

	public Portions getPortions() {
		return portions;
	}

	public Optional<Integer> getGramsPerPortion() {
		return Optional.ofNullable(gramsPerPortion);
	}

	public LocalDate getFrozenOn() {
		return frozenOn;
	}

	public BestBefore getBestBefore() {
		return bestBefore;
	}

	public UserId getFrozenBy() {
		return frozenBy;
	}

	public ItemState getState() {
		return state;
	}

	public Optional<DiscardReason> getDiscardReason() {
		return Optional.ofNullable(discardReason);
	}

	public Optional<Instant> getRemovedAt() {
		return Optional.ofNullable(removedAt);
	}

	public List<Withdrawal> getWithdrawals() {
		return Collections.unmodifiableList(withdrawals);
	}

}
