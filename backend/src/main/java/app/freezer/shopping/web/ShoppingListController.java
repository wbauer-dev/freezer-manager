package app.freezer.shopping.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdMember;
import app.freezer.shopping.application.AddEntry;
import app.freezer.shopping.application.ShoppingListManagement;
import app.freezer.shopping.domain.Entry;
import app.freezer.shopping.domain.EntryId;
import app.freezer.shopping.domain.ShoppingList;
import app.freezer.shopping.domain.Source;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * The household's shopping list. Every command answers with the whole list as it is afterwards.
 */
@RestController
@RequestMapping("/api/households/{householdId}/shopping-list")
class ShoppingListController {

	private final ShoppingListManagement shopping;

	ShoppingListController(ShoppingListManagement shopping) {
		this.shopping = shopping;
	}

	@GetMapping
	ShoppingListView list(HouseholdMember member) {
		return ShoppingListView.of(shopping.list(member));
	}

	@PostMapping("/entries")
	ShoppingListView add(HouseholdMember member, @Valid @RequestBody AddRequest request) {
		var productId = request.productId() == null ? null : ProductId.of(request.productId());
		return ShoppingListView.of(shopping.add(member, new AddEntry(productId, request.name(), request.quantity())));
	}

	@PostMapping("/entries/{entryId}/check")
	ShoppingListView check(HouseholdMember member, @PathVariable EntryId entryId) {
		return ShoppingListView.of(shopping.check(member, entryId));
	}

	@PostMapping("/entries/{entryId}/uncheck")
	ShoppingListView uncheck(HouseholdMember member, @PathVariable EntryId entryId) {
		return ShoppingListView.of(shopping.uncheck(member, entryId));
	}

	@DeleteMapping("/entries/{entryId}")
	ShoppingListView remove(HouseholdMember member, @PathVariable EntryId entryId) {
		return ShoppingListView.of(shopping.remove(member, entryId));
	}

	@PostMapping("/clear-checked")
	ShoppingListView clearChecked(HouseholdMember member) {
		return ShoppingListView.of(shopping.clearChecked(member));
	}

	/** A product of the catalog, or free text as the name. */
	record AddRequest(UUID productId, @Size(max = ShoppingList.MAX_NAME_LENGTH) String name,
			@Size(max = ShoppingList.MAX_QUANTITY_LENGTH) String quantity) {
	}

	record ShoppingListView(List<EntryView> entries) {

		static ShoppingListView of(ShoppingList list) {
			return new ShoppingListView(list.getEntries().stream().map(EntryView::of).toList());
		}

	}

	record EntryView(UUID id, UUID productId, String name, String quantity, Source source, String addedBy,
			Instant addedAt, boolean checked, String checkedBy, Instant checkedAt) {

		static EntryView of(Entry entry) {
			return new EntryView(entry.id().id(), entry.productId() == null ? null : entry.productId().id(),
					entry.name(), entry.quantity(), entry.source(), entry.addedByName(), entry.addedAt(),
					!entry.isOpen(), entry.checkedByName(), entry.checkedAt());
		}

	}

}
