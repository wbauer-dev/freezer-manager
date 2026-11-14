package app.freezer.inventory.web;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdMember;
import app.freezer.inventory.DiscardReason;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.application.EatFirstEntry;
import app.freezer.inventory.application.FreezeCommand;
import app.freezer.inventory.application.FrozenItemManagement;
import app.freezer.inventory.application.ItemDetails;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.ExpiryStatus;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.inventory.domain.FrozenItem;
import app.freezer.inventory.domain.ItemState;
import app.freezer.inventory.domain.Portions;
import app.freezer.media.PhotoId;
import app.freezer.sharedkernel.LabelCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Commands are named after the domain's language (freeze, take out, undo, discard) instead of generic updates.
 */
@RestController
@RequestMapping("/api/households/{householdId}")
class FrozenItemController {

	private final FrozenItemManagement management;

	FrozenItemController(FrozenItemManagement management) {
		this.management = management;
	}

	@GetMapping("/eat-first")
	List<EatFirstView> eatFirst(HouseholdMember member) {
		return management.eatFirst(member).stream().map(EatFirstView::of).toList();
	}

	@PostMapping("/items")
	ResponseEntity<ItemView> freeze(HouseholdMember member, @Valid @RequestBody FreezeRequest request) {
		var itemId = management.freeze(member, request.toCommand());
		return ResponseEntity.created(URI.create("/api/households/" + member.householdId() + "/items/" + itemId))
				.body(ItemView.of(management.details(member, itemId)));
	}

	@GetMapping("/items/{itemId}")
	ItemView item(HouseholdMember member, @PathVariable FrozenItemId itemId) {
		return ItemView.of(management.details(member, itemId));
	}

	@PostMapping("/items/{itemId}/take-out")
	ItemView takeOut(HouseholdMember member, @PathVariable FrozenItemId itemId,
			@Valid @RequestBody TakeOutRequest request) {
		management.takeOut(member, itemId, request.portions());
		return ItemView.of(management.details(member, itemId));
	}

	@PostMapping("/items/{itemId}/undo-take-out")
	ItemView undoTakeOut(HouseholdMember member, @PathVariable FrozenItemId itemId) {
		management.undoTakeOut(member, itemId);
		return ItemView.of(management.details(member, itemId));
	}

	@PostMapping("/items/{itemId}/photo")
	ItemView attachPhoto(HouseholdMember member, @PathVariable FrozenItemId itemId,
			@Valid @RequestBody AttachPhotoRequest request) {
		management.attachPhoto(member, itemId, PhotoId.of(request.photoId()));
		return ItemView.of(management.details(member, itemId));
	}

	@PostMapping("/items/{itemId}/discard")
	ItemView discard(HouseholdMember member, @PathVariable FrozenItemId itemId,
			@Valid @RequestBody DiscardRequest request) {
		management.discard(member, itemId, request.reason());
		return ItemView.of(management.details(member, itemId));
	}

	record FreezeRequest(@NotNull UUID productId, @NotNull UUID freezerId, @NotNull UUID compartmentId,
			@Min(1) @Max(Portions.MAX) int portions,
			@Min(1) @Max(FrozenItem.MAX_GRAMS_PER_PORTION) Integer gramsPerPortion, @NotNull LocalDate frozenOn,
			LocalDate bestBefore, @Size(max = FrozenItem.MAX_NOTE_LENGTH) String note, @Size(max = 20) String labelCode,
			UUID photoId) {

		FreezeCommand toCommand() {
			return new FreezeCommand(ProductId.of(productId), FreezerId.of(freezerId), CompartmentId.of(compartmentId),
					portions, gramsPerPortion, frozenOn, bestBefore, note, labelCode,
					photoId == null ? null : PhotoId.of(photoId));
		}
	}

	record AttachPhotoRequest(@NotNull UUID photoId) {
	}

	record TakeOutRequest(@Min(1) @Max(Portions.MAX) int portions) {
	}

	record DiscardRequest(@NotNull DiscardReason reason) {
	}

	record ItemView(UUID id, UUID productId, UUID photoId, String productName, String note, String labelCode,
			ItemState state,
			int initialPortions,
			int remainingPortions, Integer gramsPerPortion, LocalDate frozenOn, LocalDate bestBefore,
			boolean bestBeforeOverridden, ExpiryStatus expiryStatus, UUID freezerId, String freezerName,
			UUID compartmentId, String compartmentName, DiscardReason discardReason, Instant removedAt,
			boolean canUndoTakeOut, List<WithdrawalView> withdrawals) {

		static ItemView of(ItemDetails details) {
			var item = details.item();
			return new ItemView(item.getId().id(), item.getProductId().id(),
					item.getPhotoId().map(PhotoId::id).orElse(null), item.getProductName(),
					item.getNote().orElse(null), item.getLabelCode().map(LabelCode::value).orElse(null),
					item.getState(), item.getPortions().initial(), item.getPortions().remaining(),
					item.getGramsPerPortion().orElse(null), item.getFrozenOn(), item.getBestBefore().date(),
					item.getBestBefore().overridden(), details.expiryStatus(), item.getLocation().freezerId().id(),
					details.freezerName(), item.getLocation().compartmentId().id(), details.compartmentName(),
					item.getDiscardReason().orElse(null), item.getRemovedAt().orElse(null), details.canUndoTakeOut(),
					item.getWithdrawals().stream()
							.map(withdrawal -> new WithdrawalView(withdrawal.portions(), withdrawal.takenByName(),
									withdrawal.takenAt()))
							.toList());
		}
	}

	record WithdrawalView(int portions, String takenBy, Instant takenAt) {
	}

	record EatFirstView(UUID itemId, UUID productId, UUID photoId, String productName, String note,
			int remainingPortions, Integer gramsPerPortion, LocalDate frozenOn, LocalDate bestBefore,
			ExpiryStatus expiryStatus, UUID freezerId, String freezerName, UUID compartmentId,
			String compartmentName) {

		static EatFirstView of(EatFirstEntry entry) {
			return new EatFirstView(entry.itemId().id(), entry.productId().id(),
					entry.photoId() == null ? null : entry.photoId().id(), entry.productName(), entry.note(),
					entry.remainingPortions(), entry.gramsPerPortion(), entry.frozenOn(), entry.bestBefore(),
					entry.expiryStatus(), entry.freezerId().id(), entry.freezerName(), entry.compartmentId().id(),
					entry.compartmentName());
		}
	}

}
