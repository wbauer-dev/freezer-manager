package app.freezer.household.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import app.freezer.household.UserId;

class HouseholdTests {

	static final UserId ALICE = UserId.of("alice");
	static final UserId BOB = UserId.of("bob");
	static final UserId CAROL = UserId.of("carol");
	static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

	@Test
	void founderBecomesFirstOwner() {
		var household = Household.found("Miller family", ALICE, "Alice", NOW);

		assertThat(household.getMembers()).containsExactly(new Member(ALICE, "Alice", Role.OWNER, NOW));
		assertThat(household.getFoundedAt()).isEqualTo(NOW);
	}

	@Test
	void nameIsTrimmed() {
		var household = Household.found("  Miller family  ", ALICE, "Alice", NOW);

		assertThat(household.getName()).isEqualTo("Miller family");
	}

	@Test
	void rejectsBlankName() {
		assertThatIllegalArgumentException().isThrownBy(() -> Household.found("   ", ALICE, "Alice", NOW));
	}

	@Test
	void rejectsTooLongName() {
		var tooLong = "x".repeat(Household.MAX_NAME_LENGTH + 1);

		assertThatIllegalArgumentException().isThrownBy(() -> Household.found(tooLong, ALICE, "Alice", NOW));
	}

	@Test
	void knowsItsMembers() {
		var household = Household.found("Miller family", ALICE, "Alice", NOW);

		assertThat(household.hasMember(ALICE)).isTrue();
		assertThat(household.hasMember(BOB)).isFalse();
	}

	/** Alice founded the household, Bob joined with an invitation. */
	static Household aliceAndBob() {
		var household = Household.found("Miller family", ALICE, "Alice", NOW);
		var invitation = household.invite(ALICE, NOW);
		household.accept(invitation.code(), BOB, "Bob", NOW.plusSeconds(60));
		return household;
	}

	@Nested
	class Invitations {

		@Test
		void anOwnerInvitesWithAnUnguessableCodeThatIsValidForSevenDays() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);

			var invitation = household.invite(ALICE, NOW);

			assertThat(invitation.code().value()).matches("[A-Za-z0-9_-]{22}");
			assertThat(invitation.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
			assertThat(invitation.isValidAt(NOW.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue();
			assertThat(invitation.isValidAt(NOW.plus(Duration.ofDays(7)))).isFalse();
			assertThat(household.openInvitations(ALICE, NOW)).containsExactly(invitation);
		}

		@Test
		void codesAreRandom() {
			var codes = new HashSet<InvitationCode>();
			IntStream.range(0, 1000).forEach(i -> codes.add(InvitationCode.generate()));

			assertThat(codes).hasSize(1000);
		}

		@Test
		void aMalformedCodeIsReportedLikeAnUnknownOne() {
			assertThatExceptionOfType(InvalidInvitationException.class).isThrownBy(() -> InvitationCode.of("abc"));
			assertThatExceptionOfType(InvalidInvitationException.class)
					.isThrownBy(() -> InvitationCode.of("a".repeat(21) + "!"));
		}

		@Test
		void theInvitedUserJoinsAsMemberAndTheInvitationIsUsedUp() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);
			var invitation = household.invite(ALICE, NOW);
			var later = NOW.plus(Duration.ofDays(2));

			household.accept(invitation.code(), BOB, "Bob", later);

			assertThat(household.member(BOB)).contains(new Member(BOB, "Bob", Role.MEMBER, later));
			assertThat(household.openInvitations(ALICE, later)).isEmpty();
			assertThatExceptionOfType(InvalidInvitationException.class)
					.isThrownBy(() -> household.accept(invitation.code(), CAROL, "Carol", later));
		}

		@Test
		void aMemberOpeningTheLinkStaysMemberAndLeavesTheInvitationOpen() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);
			var invitation = household.invite(ALICE, NOW);

			household.accept(invitation.code(), ALICE, "Alice", NOW);

			assertThat(household.getMembers()).containsExactly(new Member(ALICE, "Alice", Role.OWNER, NOW));
			assertThat(household.openInvitations(ALICE, NOW)).containsExactly(invitation);
		}

		@Test
		void anExpiredInvitationCannotBeAccepted() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);
			var invitation = household.invite(ALICE, NOW);
			var expired = invitation.expiresAt();

			assertThatExceptionOfType(InvalidInvitationException.class)
					.isThrownBy(() -> household.accept(invitation.code(), BOB, "Bob", expired));
			assertThat(household.hasMember(BOB)).isFalse();
			assertThat(household.openInvitations(ALICE, expired)).isEmpty();
		}

		@Test
		void aRevokedInvitationCannotBeAccepted() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);
			var invitation = household.invite(ALICE, NOW);

			household.revokeInvitation(invitation.code(), ALICE);
			household.revokeInvitation(invitation.code(), ALICE);

			assertThat(household.openInvitations(ALICE, NOW)).isEmpty();
			assertThatExceptionOfType(InvalidInvitationException.class)
					.isThrownBy(() -> household.accept(invitation.code(), BOB, "Bob", NOW));
		}

		@Test
		void onlyOwnersInviteAndSeeOrRevokeInvitations() {
			var household = aliceAndBob();
			var invitation = household.invite(ALICE, NOW);

			assertThatExceptionOfType(OwnerRequiredException.class).isThrownBy(() -> household.invite(BOB, NOW));
			assertThatExceptionOfType(OwnerRequiredException.class)
					.isThrownBy(() -> household.openInvitations(BOB, NOW));
			assertThatExceptionOfType(OwnerRequiredException.class)
					.isThrownBy(() -> household.revokeInvitation(invitation.code(), BOB));
			assertThatExceptionOfType(OwnerRequiredException.class).isThrownBy(() -> household.invite(CAROL, NOW));
		}

		@Test
		void aHouseholdHasALimitedNumberOfOpenInvitations() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);
			IntStream.range(0, Household.MAX_OPEN_INVITATIONS).forEach(i -> household.invite(ALICE, NOW));

			assertThatExceptionOfType(HouseholdRuleViolation.class).isThrownBy(() -> household.invite(ALICE, NOW))
					.withMessage("household.invitation.too-many");

			// Expired invitations don't count; they are cleared away.
			var nextWeek = NOW.plus(Invitation.VALIDITY);
			var invitation = household.invite(ALICE, nextWeek);
			assertThat(household.openInvitations(ALICE, nextWeek)).containsExactly(invitation);
		}

	}

	@Nested
	class Membership {

		@Test
		void anOwnerMakesAMemberAnOwner() {
			var household = aliceAndBob();

			household.makeOwner(BOB, ALICE);
			household.makeOwner(BOB, ALICE);

			assertThat(household.member(BOB)).hasValueSatisfying(bob -> assertThat(bob.isOwner()).isTrue());
		}

		@Test
		void onlyOwnersMakeOwners() {
			var household = aliceAndBob();

			assertThatExceptionOfType(OwnerRequiredException.class).isThrownBy(() -> household.makeOwner(BOB, BOB));
		}

		@Test
		void onlyMembersCanBecomeOwners() {
			var household = aliceAndBob();

			assertThatExceptionOfType(HouseholdRuleViolation.class)
					.isThrownBy(() -> household.makeOwner(CAROL, ALICE))
					.withMessage("household.member.unknown");
		}

		@Test
		void anOwnerRemovesAMember() {
			var household = aliceAndBob();

			household.remove(BOB, ALICE);
			household.remove(BOB, ALICE);

			assertThat(household.hasMember(BOB)).isFalse();
		}

		@Test
		void onlyOwnersRemoveMembers() {
			var household = aliceAndBob();

			assertThatExceptionOfType(OwnerRequiredException.class).isThrownBy(() -> household.remove(ALICE, BOB));
			assertThat(household.hasMember(ALICE)).isTrue();
		}

		@Test
		void aMemberLeaves() {
			var household = aliceAndBob();

			household.leave(BOB);

			assertThat(household.hasMember(BOB)).isFalse();
		}

		@Test
		void theLastOwnerCannotLeaveWhileOthersRemain() {
			var household = aliceAndBob();

			assertThatExceptionOfType(HouseholdRuleViolation.class).isThrownBy(() -> household.leave(ALICE))
					.withMessage("household.member.last-owner");
			assertThatExceptionOfType(HouseholdRuleViolation.class).isThrownBy(() -> household.remove(ALICE, ALICE))
					.withMessage("household.member.last-owner");
			assertThat(household.hasMember(ALICE)).isTrue();
		}

		@Test
		void theLastOwnerHandsOverBeforeLeaving() {
			var household = aliceAndBob();

			household.makeOwner(BOB, ALICE);
			household.leave(ALICE);

			assertThat(household.getMembers()).singleElement().satisfies(bob -> {
				assertThat(bob.userId()).isEqualTo(BOB);
				assertThat(bob.isOwner()).isTrue();
			});
		}

		@Test
		void ownersCanRemoveEachOtherButNeverTheLastOwner() {
			var household = aliceAndBob();
			household.makeOwner(BOB, ALICE);

			household.remove(ALICE, BOB);

			assertThat(household.getMembers()).extracting(Member::userId).containsExactly(BOB);
			assertThatExceptionOfType(HouseholdRuleViolation.class).isThrownBy(() -> household.leave(BOB));
		}

		@Test
		void theOnlyMemberCannotLeave() {
			var household = Household.found("Miller family", ALICE, "Alice", NOW);

			assertThatExceptionOfType(HouseholdRuleViolation.class).isThrownBy(() -> household.leave(ALICE))
					.withMessage("household.member.only-member");
		}

	}

}
