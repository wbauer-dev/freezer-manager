package app.freezer.labeling.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.sharedkernel.LabelCode;

/**
 * A label: the identity behind a QR code. What is printed on a sticker is not part of it (see {@link Sticker}).
 * <p>
 * Its life follows the item it is attached to, which Labeling learns about from Inventory's events:
 *
 * <pre>
 *                 ItemFrozen            ItemConsumed / ItemDiscarded
 * one-time:      (created) ──▶ ATTACHED ──────────────────────────▶ USED_UP
 * persistent: FREE ──────────▶ ATTACHED ──────────────────────────▶ FREE
 * </pre>
 *
 * Undoing a take-out that had consumed the item attaches the label again, unless a persistent label has been put on
 * another item in the meantime.
 * <p>
 * The events arrive asynchronously: late, again, and not necessarily in order — a reaction that failed is retried
 * after later events of the item have been applied. Each event carries the item's revision, and the label remembers
 * the newest one it has applied for its item. An event of that item whose revision is not newer is <em>stale</em> and
 * ignored, so an old "consumed" cannot release a label whose item came back into the freezer. Revision {@value
 * #UNKNOWN_REVISION} means unknown (events published before items had revisions): such an event is applied as it
 * comes, and a label that knows no revision yet accepts every event.
 */
@Table(schema = "labeling", name = "label")
public class Label implements AggregateRoot<Label, LabelCode> {

	/** The revision of an event published before items had revisions, and of a label that has applied none. */
	public static final long UNKNOWN_REVISION = 0;

	@Identity
	private final LabelCode code;
	private final HouseholdId householdId;
	private final LabelKind kind;
	private LabelState state;
	/** The item the label is attached to, or the last one it was attached to. */
	private FrozenItemId itemId;
	/** The newest revision of {@link #itemId} whose event the label has applied. */
	private long itemRevision;
	private final Instant createdAt;
	@Version
	private Long version;

	Label(LabelCode code, HouseholdId householdId, LabelKind kind, LabelState state, FrozenItemId itemId,
			long itemRevision, Instant createdAt, Long version) {
		this.code = code;
		this.householdId = householdId;
		this.kind = kind;
		this.state = state;
		this.itemId = itemId;
		this.itemRevision = itemRevision;
		this.createdAt = createdAt;
		this.version = version;
	}

	/**
	 * A label printed for one item that was just frozen.
	 *
	 * @param revision the item's revision when it was frozen
	 */
	public static Label oneTimeFor(LabelCode code, HouseholdId householdId, FrozenItemId itemId, long revision,
			Instant now) {
		return new Label(code, householdId, LabelKind.ONE_TIME, LabelState.ATTACHED, itemId, revision, now, null);
	}

	/** A label for a container, lid or bag clip, printed once and used for many fillings. */
	public static Label persistent(HouseholdId householdId, Instant now) {
		return new Label(LabelCode.generate(), householdId, LabelKind.PERSISTENT, LabelState.FREE, null,
				UNKNOWN_REVISION, now, null);
	}

	/**
	 * Puts a free label on an item that was just frozen. Attaching it again to the item it is on changes nothing, so
	 * an event that is delivered twice does no harm; a stale one is ignored.
	 *
	 * @param revision the item's revision when it was frozen
	 */
	public void attach(FrozenItemId item, long revision) {
		if (isStale(item, revision)) {
			return;
		}
		if (state == LabelState.ATTACHED && item.equals(itemId)) {
			applied(revision);
			return;
		}
		if (state != LabelState.FREE) {
			throw new LabelingRuleViolation("labeling.label." + state.name().toLowerCase(Locale.ROOT).replace('_', '-'),
					code.formatted());
		}
		if (!item.equals(itemId)) {
			// Another item: it counts its revisions from the start.
			this.itemId = item;
			this.itemRevision = UNKNOWN_REVISION;
		}
		this.state = LabelState.ATTACHED;
		applied(revision);
	}

	/**
	 * The item was eaten or discarded. Ignored if the label is not attached to that item, or if the event is stale.
	 *
	 * @param revision the item's revision with that change
	 */
	public void release(FrozenItemId item, long revision) {
		if (!item.equals(itemId) || isStale(item, revision)) {
			return;
		}
		applied(revision);
		if (state == LabelState.ATTACHED) {
			state = kind == LabelKind.ONE_TIME ? LabelState.USED_UP : LabelState.FREE;
		}
	}

	/**
	 * A take-out that had consumed the item was undone. Ignored if the label has moved on to another item, or if the
	 * event is stale.
	 *
	 * @param revision the item's revision with that change
	 */
	public void reattach(FrozenItemId item, long revision) {
		if (!item.equals(itemId) || isStale(item, revision)) {
			return;
		}
		applied(revision);
		state = LabelState.ATTACHED;
	}

	/** Whether an event of the item is not newer than one the label has already applied for it. */
	private boolean isStale(FrozenItemId item, long revision) {
		return item.equals(itemId) && revision != UNKNOWN_REVISION && revision <= itemRevision;
	}

	/**
	 * Remembers that the item's event was applied — also when it changed nothing, e.g. an undo while the label was
	 * still attached: an older "consumed" arriving afterwards must not release the label.
	 */
	private void applied(long revision) {
		itemRevision = Math.max(itemRevision, revision);
	}

	public boolean belongsTo(HouseholdId householdId) {
		return this.householdId.equals(householdId);
	}

	public boolean isAttached() {
		return state == LabelState.ATTACHED;
	}

	@Override
	public LabelCode getId() {
		return code;
	}

	public HouseholdId getHouseholdId() {
		return householdId;
	}

	public LabelKind getKind() {
		return kind;
	}

	public LabelState getState() {
		return state;
	}

	public Optional<FrozenItemId> getItemId() {
		return Optional.ofNullable(itemId);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
