package app.freezer.inventory.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.household.HouseholdMember;
import app.freezer.inventory.application.FreezerManagement;
import app.freezer.inventory.application.FreezerOverview;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.Freezer;
import app.freezer.inventory.domain.FreezerId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/households/{householdId}/freezers")
class FreezerController {

	private final FreezerManagement management;

	FreezerController(FreezerManagement management) {
		this.management = management;
	}

	@GetMapping
	List<FreezerView> freezers(HouseholdMember member) {
		return management.overview(member).stream().map(FreezerView::of).toList();
	}

	@PostMapping
	ResponseEntity<Map<String, UUID>> setUpFreezer(HouseholdMember member,
			@Valid @RequestBody SetUpFreezerRequest request) {
		var id = management.setUpFreezer(member, request.name(), request.compartments());
		return ResponseEntity.created(URI.create("/api/households/" + member.householdId() + "/freezers/" + id))
				.body(Map.of("id", id.id()));
	}

	@DeleteMapping("/{freezerId}")
	ResponseEntity<Void> removeFreezer(HouseholdMember member, @PathVariable FreezerId freezerId) {
		management.removeFreezer(member, freezerId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{freezerId}/compartments")
	ResponseEntity<Void> addCompartment(HouseholdMember member, @PathVariable FreezerId freezerId,
			@Valid @RequestBody AddCompartmentRequest request) {
		management.addCompartment(member, freezerId, request.name());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{freezerId}/compartments/{compartmentId}")
	ResponseEntity<Void> removeCompartment(HouseholdMember member, @PathVariable FreezerId freezerId,
			@PathVariable CompartmentId compartmentId) {
		management.removeCompartment(member, freezerId, compartmentId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{freezerId}/rearrange-compartments")
	ResponseEntity<Void> rearrangeCompartments(HouseholdMember member, @PathVariable FreezerId freezerId,
			@Valid @RequestBody RearrangeCompartmentsRequest request) {
		management.rearrangeCompartments(member, freezerId,
				request.compartmentIds().stream().map(CompartmentId::of).toList());
		return ResponseEntity.noContent().build();
	}

	record RearrangeCompartmentsRequest(@NotEmpty List<@NotNull UUID> compartmentIds) {
	}

	record SetUpFreezerRequest(@NotBlank @Size(max = Freezer.MAX_NAME_LENGTH) String name,
			@NotEmpty @Size(max = Freezer.MAX_COMPARTMENTS)
			List<@NotBlank @Size(max = Freezer.MAX_NAME_LENGTH) String> compartments) {
	}

	record AddCompartmentRequest(@NotBlank @Size(max = Freezer.MAX_NAME_LENGTH) String name) {
	}

	record FreezerView(UUID id, String name, List<CompartmentView> compartments) {

		static FreezerView of(FreezerOverview freezer) {
			return new FreezerView(freezer.id().id(), freezer.name(), freezer.compartments().stream()
					.map(compartment -> new CompartmentView(compartment.id().id(), compartment.name(),
							compartment.itemCount()))
					.toList());
		}
	}

	record CompartmentView(UUID id, String name, int itemCount) {
	}

}
