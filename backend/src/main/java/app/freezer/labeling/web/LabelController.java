package app.freezer.labeling.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdMember;
import app.freezer.inventory.FrozenItemId;
import app.freezer.labeling.application.LabelPrinting;
import app.freezer.labeling.application.LabelStock;
import app.freezer.labeling.application.ScanResolution;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.LabelKind;
import app.freezer.labeling.domain.LabelState;
import app.freezer.labeling.domain.Sticker;
import app.freezer.labeling.domain.StickerLayout;
import app.freezer.sharedkernel.LabelCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
class LabelController {

	private final ScanResolution scans;
	private final LabelPrinting printing;

	LabelController(ScanResolution scans, LabelPrinting printing) {
		this.scans = scans;
		this.printing = printing;
	}

	/** What a scanned code means. The household comes from the label, so the path does not name one. */
	@GetMapping("/api/labels/{code}")
	ScanView scan(@PathVariable String code, CurrentUser user) {
		return ScanView.of(scans.resolve(user, LabelCode.parse(code)));
	}

	/** Persistent labels to choose from when freezing something into a labelled container. */
	@GetMapping("/api/households/{householdId}/labels/free")
	List<FreeLabelView> freeLabels(HouseholdMember member) {
		return printing.freeLabels(member).stream()
				.map(label -> new FreeLabelView(label.code().value(), label.lastContents()))
				.toList();
	}

	@GetMapping("/api/households/{householdId}/labels/stock")
	LabelStock stock(HouseholdMember member) {
		return printing.stock(member);
	}

	@PostMapping("/api/households/{householdId}/labels/persistent")
	ResponseEntity<List<String>> createPersistentLabels(HouseholdMember member,
			@Valid @RequestBody CreatePersistentRequest request) {
		var codes = printing.createPersistentLabels(member, request.count());
		return ResponseEntity.status(HttpStatus.CREATED).body(codes.stream().map(LabelCode::value).toList());
	}

	@GetMapping("/api/households/{householdId}/label-sheet")
	SheetView sheet(HouseholdMember member) {
		var overview = printing.overview(member);
		return new SheetView(overview.format().columns(), overview.format().rows(), overview.usedPositions(),
				overview.nextPosition(), overview.queue().stream().map(StickerView::of).toList());
	}

	@PostMapping("/api/households/{householdId}/label-sheet/queue")
	ResponseEntity<Void> enqueue(HouseholdMember member, @Valid @RequestBody EnqueueRequest request) {
		printing.enqueue(member, LabelCode.parse(request.code()), request.layout(),
				request.itemId() == null ? null : FrozenItemId.of(request.itemId()));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/households/{householdId}/label-sheet/queue/{code}")
	ResponseEntity<Void> dequeue(HouseholdMember member, @PathVariable String code,
			@RequestParam StickerLayout layout) {
		printing.dequeue(member, LabelCode.parse(code), layout);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/households/{householdId}/label-sheet/print")
	ResponseEntity<byte[]> print(HouseholdMember member, Locale locale) {
		var pdf = printing.print(member, locale);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("labels.pdf").build().toString())
				.body(pdf);
	}

	@PostMapping("/api/households/{householdId}/label-sheet/new-sheet")
	ResponseEntity<Void> startNewSheet(HouseholdMember member) {
		printing.startNewSheet(member);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/households/{householdId}/label-sheet/positions/{position}/toggle")
	ResponseEntity<Void> togglePosition(HouseholdMember member, @PathVariable int position) {
		printing.togglePosition(member, position);
		return ResponseEntity.noContent().build();
	}

	record ScanView(String code, UUID householdId, LabelKind kind, LabelState state, UUID itemId) {

		static ScanView of(Label label) {
			return new ScanView(label.getId().value(), label.getHouseholdId().id(), label.getKind(), label.getState(),
					label.getItemId().map(FrozenItemId::id).orElse(null));
		}
	}

	record SheetView(int columns, int rows, Set<Integer> usedPositions, int nextPosition, List<StickerView> queue) {
	}

	record StickerView(String code, StickerLayout layout, String title, LocalDate frozenOn, LocalDate bestBefore) {

		static StickerView of(Sticker sticker) {
			return new StickerView(sticker.code().value(), sticker.layout(), sticker.title(), sticker.frozenOn(),
					sticker.bestBefore());
		}
	}

	/**
	 * @param itemId optional: the item a sticker with contents describes; the label's current item if missing
	 */
	record EnqueueRequest(@NotBlank String code, @NotNull StickerLayout layout, UUID itemId) {
	}

	record CreatePersistentRequest(@Min(1) @Max(LabelPrinting.MAX_PERSISTENT_AT_ONCE) int count) {
	}

	record FreeLabelView(String code, String lastContents) {
	}

}
