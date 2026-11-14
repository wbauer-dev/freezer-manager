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
 */
@Table(schema = "labeling", name = "label")
public class Label implements AggregateRoot<Label, LabelCode> {

	@Identity
	private final LabelCode code;
	private final HouseholdId householdId;
	private final LabelKind kind;
	private LabelState state;
	/** The item the label is attached to, or the last one it was attached to. */
	private FrozenItemId itemId;
	private final Instant createdAt;
	@Version
	private Long version;

	Label(LabelCode code, HouseholdId householdId, LabelKind kind, LabelState state, FrozenItemId itemId,
			Instant createdAt, Long version) {
		this.code = code;
		this.householdId = householdId;
		this.kind = kind;
		this.state = state;
		this.itemId = itemId;
		this.createdAt = createdAt;
		this.version = version;
	}

	/** A label printed for one item that was just frozen. */
	public static Label oneTimeFor(LabelCode code, HouseholdId householdId, FrozenItemId itemId, Instant now) {
		return new Label(code, householdId, LabelKind.ONE_TIME, LabelState.ATTACHED, itemId, now, null);
	}

	/** A label for a container, lid or bag clip, printed once and used for many fillings. */
	public static Label persistent(HouseholdId householdId, Instant now) {
		return new Label(LabelCode.generate(), householdId, LabelKind.PERSISTENT, LabelState.FREE, null, now, null);
	}

	/**
	 * Puts a free label on an item that was just frozen. Attaching it again to the item it is on changes nothing, so
	 * an event that is delivered twice does no harm.
	 */
	public void attach(FrozenItemId item) {
		if (state == LabelState.ATTACHED && item.equals(itemId)) {
			return;
		}
		if (state != LabelState.FREE) {
			throw new LabelingRuleViolation("labeling.label." + state.name().toLowerCase(Locale.ROOT).replace('_', '-'),
					code.formatted());
		}
		this.state = LabelState.ATTACHED;
		this.itemId = item;
	}

	/** The item was eaten or discarded. Ignored if the label is not attached to that item. */
	public void release(FrozenItemId item) {
		if (state == LabelState.ATTACHED && item.equals(itemId)) {
			state = kind == LabelKind.ONE_TIME ? LabelState.USED_UP : LabelState.FREE;
		}
	}

	/** A take-out that had consumed the item was undone. Ignored if the label has moved on to another item. */
	public void reattach(FrozenItemId item) {
		if (state != LabelState.ATTACHED && item.equals(itemId)) {
			state = LabelState.ATTACHED;
		}
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
