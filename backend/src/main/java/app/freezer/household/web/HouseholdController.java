package app.freezer.household.web;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.household.application.HouseholdManagement;
import app.freezer.household.domain.Household;
import app.freezer.household.domain.Member;
import app.freezer.household.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/households")
class HouseholdController {

	private final HouseholdManagement management;

	HouseholdController(HouseholdManagement management) {
		this.management = management;
	}

	@GetMapping
	List<HouseholdSummary> myHouseholds(CurrentUser user) {
		return management.householdsOf(user).stream()
				.map(household -> HouseholdSummary.of(household, user.id()))
				.toList();
	}

	@PostMapping
	ResponseEntity<HouseholdDetails> foundHousehold(@Valid @RequestBody FoundHouseholdRequest request,
			CurrentUser user) {
		var household = management.foundHousehold(request.name(), user);
		return ResponseEntity.created(URI.create("/api/households/" + household.getId()))
				.body(HouseholdDetails.of(household));
	}

	@GetMapping("/{id}")
	HouseholdDetails household(@PathVariable HouseholdId id, CurrentUser user) {
		return HouseholdDetails.of(management.household(id, user));
	}

	record FoundHouseholdRequest(@NotBlank @Size(max = Household.MAX_NAME_LENGTH) String name) {
	}

	record HouseholdSummary(UUID id, String name, Role myRole, int memberCount) {

		static HouseholdSummary of(Household household, UserId me) {
			var myRole = household.member(me).map(Member::role).orElseThrow();
			return new HouseholdSummary(household.getId().id(), household.getName(), myRole,
					household.getMembers().size());
		}
	}

	record HouseholdDetails(UUID id, String name, Instant foundedAt, List<MemberView> members) {

		static HouseholdDetails of(Household household) {
			var members = household.getMembers().stream()
					.sorted(Comparator.comparing(Member::joinedAt))
					.map(MemberView::of)
					.toList();
			return new HouseholdDetails(household.getId().id(), household.getName(), household.getFoundedAt(), members);
		}
	}

	record MemberView(String userId, String displayName, Role role, Instant joinedAt) {

		static MemberView of(Member member) {
			return new MemberView(member.userId().value(), member.displayName(), member.role(), member.joinedAt());
		}
	}

}
