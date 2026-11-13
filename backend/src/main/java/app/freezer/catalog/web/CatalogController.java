package app.freezer.catalog.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.catalog.ProductId;
import app.freezer.catalog.application.CatalogAdministration;
import app.freezer.catalog.application.CatalogSetup;
import app.freezer.catalog.application.CatalogTemplates;
import app.freezer.catalog.domain.FoodCategory;
import app.freezer.catalog.domain.FoodCategoryId;
import app.freezer.catalog.domain.Product;
import app.freezer.household.HouseholdMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The household's catalog. The request's language decides which system defaults a household starts with.
 */
@RestController
class CatalogController {

	private final CatalogAdministration administration;
	private final CatalogSetup setup;
	private final CatalogTemplates templates;

	CatalogController(CatalogAdministration administration, CatalogSetup setup, CatalogTemplates templates) {
		this.administration = administration;
		this.setup = setup;
		this.templates = templates;
	}

	@GetMapping("/api/catalog/languages")
	List<LanguageView> languages() {
		return templates.all().stream()
				.map(template -> new LanguageView(template.language(), template.name()))
				.toList();
	}

	@GetMapping("/api/households/{householdId}/catalog")
	CatalogView catalog(HouseholdMember member, Locale locale) {
		setup.ensureStarted(member.householdId(), locale.getLanguage());
		return administration.catalog(member)
				.map(catalog -> new CatalogView(catalog.getLanguage().orElse(null), catalog.getStartedAt()))
				.orElseThrow();
	}

	@PostMapping("/api/households/{householdId}/catalog/reset")
	ResponseEntity<Void> reset(HouseholdMember member, @Valid @RequestBody ResetRequest request) {
		setup.reset(member, request.mode() == ResetMode.DEFAULTS, request.language());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/households/{householdId}/food-categories")
	List<FoodCategoryView> categories(HouseholdMember member, Locale locale) {
		return administration.categories(member, locale.getLanguage()).stream().map(FoodCategoryView::of).toList();
	}

	@PostMapping("/api/households/{householdId}/food-categories")
	ResponseEntity<FoodCategoryView> defineCategory(HouseholdMember member, Locale locale,
			@Valid @RequestBody CategoryRequest request) {
		var category = administration.defineCategory(member, request.name(), request.emoji(), request.shelfLifeMonths(),
				locale.getLanguage());
		return ResponseEntity
				.created(URI.create("/api/households/" + member.householdId() + "/food-categories/" + category.getId()))
				.body(FoodCategoryView.of(category));
	}

	@PutMapping("/api/households/{householdId}/food-categories/{categoryId}")
	FoodCategoryView editCategory(HouseholdMember member, @PathVariable UUID categoryId,
			@Valid @RequestBody CategoryRequest request) {
		return FoodCategoryView.of(administration.editCategory(member, FoodCategoryId.of(categoryId), request.name(),
				request.emoji(), request.shelfLifeMonths()));
	}

	@DeleteMapping("/api/households/{householdId}/food-categories/{categoryId}")
	ResponseEntity<Void> removeCategory(HouseholdMember member, @PathVariable UUID categoryId) {
		administration.removeCategory(member, FoodCategoryId.of(categoryId));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/households/{householdId}/products")
	List<ProductView> products(HouseholdMember member, Locale locale) {
		return administration.products(member, locale.getLanguage()).stream().map(ProductView::of).toList();
	}

	@PostMapping("/api/households/{householdId}/products")
	ResponseEntity<ProductView> defineProduct(HouseholdMember member, @Valid @RequestBody ProductRequest request) {
		var product = administration.defineProduct(member, request.name(), FoodCategoryId.of(request.categoryId()));
		return ResponseEntity
				.created(URI.create("/api/households/" + member.householdId() + "/products/" + product.getId()))
				.body(ProductView.of(product));
	}

	@PutMapping("/api/households/{householdId}/products/{productId}")
	ProductView editProduct(HouseholdMember member, @PathVariable UUID productId,
			@Valid @RequestBody ProductRequest request) {
		return ProductView.of(administration.editProduct(member, ProductId.of(productId), request.name(),
				FoodCategoryId.of(request.categoryId())));
	}

	@DeleteMapping("/api/households/{householdId}/products/{productId}")
	ResponseEntity<Void> removeProduct(HouseholdMember member, @PathVariable UUID productId) {
		administration.removeProduct(member, ProductId.of(productId));
		return ResponseEntity.noContent().build();
	}

	enum ResetMode {
		EMPTY, DEFAULTS
	}

	record ResetRequest(@NotNull ResetMode mode, @Size(max = 8) String language) {
	}

	record CategoryRequest(@NotBlank @Size(max = Product.MAX_NAME_LENGTH) String name,
			@NotBlank @Size(max = FoodCategory.MAX_EMOJI_LENGTH) String emoji,
			@Min(1) @Max(FoodCategory.MAX_SHELF_LIFE_MONTHS) int shelfLifeMonths) {
	}

	record ProductRequest(@NotBlank @Size(max = Product.MAX_NAME_LENGTH) String name, @NotNull UUID categoryId) {
	}

	record LanguageView(String code, String name) {
	}

	/** @param startedWith the language of the system defaults the catalog started with; {@code null} if empty */
	record CatalogView(String startedWith, Instant startedAt) {
	}

	record ProductView(UUID id, String name, UUID categoryId) {

		static ProductView of(Product product) {
			return new ProductView(product.getId().id(), product.getName(), product.getCategoryId().id());
		}
	}

	record FoodCategoryView(UUID id, String name, String emoji, int shelfLifeMonths) {

		static FoodCategoryView of(FoodCategory category) {
			return new FoodCategoryView(category.getId().id(), category.getName(), category.getEmoji(),
					category.getShelfLifeMonths());
		}
	}

}
