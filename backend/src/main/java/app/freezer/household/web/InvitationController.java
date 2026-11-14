package app.freezer.household.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdMember;
import app.freezer.household.application.HouseholdManagement;
import app.freezer.household.domain.Invitation;
import app.freezer.household.domain.InvitationCode;
import app.freezer.household.web.HouseholdController.HouseholdSummary;

/**
 * Owners invite people with a link; whoever opens it, logged in, joins the household.
 * <p>
 * Codes are taken as plain strings and parsed here, so a malformed code is reported exactly like an unknown one.
 */
@RestController
class InvitationController {

	private final HouseholdManagement management;

	InvitationController(HouseholdManagement management) {
		this.management = management;
	}

	@PostMapping("/api/households/{householdId}/invitations")
	ResponseEntity<InvitationView> invite(HouseholdMember owner) {
		return ResponseEntity.status(HttpStatus.CREATED).body(InvitationView.of(management.invite(owner)));
	}

	@GetMapping("/api/households/{householdId}/invitations")
	List<InvitationView> openInvitations(HouseholdMember owner) {
		return management.openInvitations(owner).stream().map(InvitationView::of).toList();
	}

	@DeleteMapping("/api/households/{householdId}/invitations/{code}")
	ResponseEntity<Void> revoke(HouseholdMember owner, @PathVariable String code) {
		management.revokeInvitation(owner, InvitationCode.of(code));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/invitations/{code}/accept")
	HouseholdSummary accept(@PathVariable String code, CurrentUser user) {
		return HouseholdSummary.of(management.acceptInvitation(InvitationCode.of(code), user), user.id());
	}

	record InvitationView(String code, Instant createdAt, Instant expiresAt) {

		static InvitationView of(Invitation invitation) {
			return new InvitationView(invitation.code().value(), invitation.createdAt(), invitation.expiresAt());
		}
	}

}
