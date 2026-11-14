package app.freezer.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import app.freezer.TestcontainersConfiguration;
import app.freezer.household.domain.Households;
import app.freezer.household.domain.InvitationCode;

/**
 * Invitations and membership changes through the REST API, with two (and sometimes three) real users: owners invite,
 * the invited user joins and shares the household's data, members leave or are removed — and the household always
 * keeps an owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MembershipApiIntegrationTests {

	static final String INVALID_INVITATION = "This invitation does not work (any more): it may have expired or been "
			+ "used already. Ask someone in the household for a new link.";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoSpyBean
	Households households;

	RequestPostProcessor alice;
	RequestPostProcessor bob;
	RequestPostProcessor carol;
	String bobId;
	String household;

	@BeforeEach
	void aliceFoundsAHousehold() {
		alice = user("alice-" + UUID.randomUUID(), "Alice");
		bobId = "bob-" + UUID.randomUUID();
		bob = user(bobId, "Bob");
		carol = user("carol-" + UUID.randomUUID(), "Carol");
		household = json(post(alice, "/api/households", "{ \"name\": \"Alice's flat\" }"), "$.id");
	}

	@Test
	void anInvitedUserJoinsAndSharesTheHousehold() {
		assertThat(get(bob, api(""))).hasStatus(HttpStatus.NOT_FOUND);

		var invitation = post(alice, api("/invitations"));
		assertThat(invitation).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.code").asString()
				.matches("[A-Za-z0-9_-]{22}");
		var code = json(invitation, "$.code");
		assertThat(get(alice, api("/invitations"))).hasStatusOk().bodyJson().extractingPath("$[*].code")
				.isEqualTo(List.of(code));

		assertThat(post(bob, "/api/invitations/" + code + "/accept")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "id": "%s", "name": "Alice's flat", "myRole": "MEMBER", "memberCount": 2 }
				""".formatted(household));

		assertThat(get(bob, "/api/households")).hasStatusOk().bodyJson().extractingPath("$[*].id")
				.isEqualTo(List.of(household));
		assertThat(get(bob, api(""))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "Alice's flat", "myRole": "MEMBER", "members": [
				  { "displayName": "Alice", "role": "OWNER", "me": false },
				  { "userId": "%s", "displayName": "Bob", "role": "MEMBER", "me": true }
				] }
				""".formatted(bobId));
		// The invitation is used up.
		assertThat(get(alice, api("/invitations"))).hasStatusOk().bodyJson().isEqualTo("[]");

		// Both work on the same shopping list.
		post(alice, api("/shopping-list/entries"), "{ \"name\": \"Milk\" }");
		post(bob, api("/shopping-list/entries"), "{ \"name\": \"Bread\" }");
		assertThat(get(alice, api("/shopping-list"))).hasStatusOk().bodyJson().extractingPath("$.entries[*].addedBy")
				.isEqualTo(List.of("Alice", "Bob"));
		assertThat(get(bob, api("/shopping-list"))).hasStatusOk().bodyJson().extractingPath("$.entries[*].name")
				.isEqualTo(List.of("Milk", "Bread"));
	}

	@Test
	void aMemberOpeningTheLinkAgainJustGoesToTheHousehold() {
		var code = invite();

		assertThat(post(alice, "/api/invitations/" + code + "/accept")).hasStatusOk().bodyJson()
				.isLenientlyEqualTo("""
						{ "id": "%s", "myRole": "OWNER", "memberCount": 1 }
						""".formatted(household));
		// Still open for whoever it was meant for.
		assertThat(get(alice, api("/invitations"))).bodyJson().extractingPath("$[*].code").isEqualTo(List.of(code));
		assertThat(post(bob, "/api/invitations/" + code + "/accept")).hasStatusOk();
	}

	@Test
	void usedExpiredRevokedAndUnknownInvitationsAllGetTheSameAnswer() {
		var used = invite();
		post(bob, "/api/invitations/" + used + "/accept");

		var revoked = invite();
		assertThat(delete(alice, api("/invitations/" + revoked))).hasStatus(HttpStatus.NO_CONTENT);

		var expired = invite();
		jdbc.sql("update household.invitation set expires_at = now() - interval '1 second' where code = ?")
				.param(expired)
				.update();
		assertThat(get(alice, api("/invitations"))).bodyJson().isEqualTo("[]");

		for (var code : List.of(used, revoked, expired, InvitationCode.generate().value(), "not-a-code")) {
			assertThat(post(carol, "/api/invitations/" + code + "/accept")).as(code)
					.hasStatus(HttpStatus.NOT_FOUND)
					.bodyJson().extractingPath("$.detail").isEqualTo(INVALID_INVITATION);
		}
		assertThat(get(carol, "/api/households")).bodyJson().isEqualTo("[]");
		assertThat(mvc.post().uri("/api/invitations/" + used + "/accept").with(carol).header("Accept-Language", "de"))
				.hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.detail").asString()
				.startsWith("Diese Einladung gilt nicht (mehr)");
	}

	@Test
	void onlyOwnersInviteAndManageMembers() {
		join(bob);

		for (var forbidden : List.of(post(bob, api("/invitations")), get(bob, api("/invitations")),
				delete(bob, api("/invitations/" + invite())), delete(bob, api("/members/" + aliceId())),
				post(bob, api("/members/" + bobId + "/make-owner")))) {
			assertThat(forbidden).hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.detail")
					.isEqualTo("Only owners can do that.");
		}
		assertThat(get(alice, api(""))).bodyJson().extractingPath("$.members[*].role")
				.isEqualTo(List.of("OWNER", "MEMBER"));
	}

	@Test
	void ownersRemoveMembers() {
		join(bob);
		post(bob, api("/shopping-list/entries"), "{ \"name\": \"Milk\" }");

		assertThat(delete(alice, api("/members/" + bobId))).hasStatusOk().bodyJson()
				.extractingPath("$.members[*].displayName").isEqualTo(List.of("Alice"));

		assertThat(get(bob, api(""))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(bob, api("/shopping-list"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(bob, "/api/households")).bodyJson().isEqualTo("[]");
		// What Bob added stays with the household.
		assertThat(get(alice, api("/shopping-list"))).bodyJson().extractingPath("$.entries[*].name")
				.isEqualTo(List.of("Milk"));
	}

	@Test
	void membersLeave() {
		join(bob);

		assertThat(post(bob, api("/leave"))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(get(bob, api(""))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(alice, api(""))).bodyJson().extractingPath("$.members[*].displayName")
				.isEqualTo(List.of("Alice"));
	}

	@Test
	void theLastOwnerHandsTheHouseholdOverBeforeLeaving() {
		join(bob);

		assertThat(post(alice, api("/leave"))).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.detail")
				.isEqualTo("The household needs an owner. Make another member an owner before you leave.");
		assertThat(delete(alice, api("/members/" + aliceId()))).hasStatus(HttpStatus.CONFLICT);

		assertThat(post(alice, api("/members/" + bobId + "/make-owner"))).hasStatusOk().bodyJson()
				.extractingPath("$.members[*].role").isEqualTo(List.of("OWNER", "OWNER"));
		assertThat(post(alice, api("/leave"))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(get(alice, api(""))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(bob, api(""))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "myRole": "OWNER", "members": [ { "displayName": "Bob", "role": "OWNER", "me": true } ] }
				""");
		// The only member can't leave: a household is never left empty.
		assertThat(post(bob, api("/leave"))).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.detail")
				.isEqualTo("You are the only member, so you cannot leave the household.");
	}

	@Test
	void outsidersCannotChangeTheHousehold() {
		var code = invite();

		assertThat(post(carol, api("/leave"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(carol, api("/invitations"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(carol, api("/invitations/" + code))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(carol, api("/members/" + aliceId()))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aChangeThatCollidedWithAnotherOneIsAConflict() {
		var code = invite();
		doThrow(new OptimisticLockingFailureException("collided")).when(households).save(any());

		assertThat(post(bob, "/api/invitations/" + code + "/accept")).hasStatus(HttpStatus.CONFLICT)
				.bodyJson().extractingPath("$.detail")
				.isEqualTo("Someone else changed this at the same time. Please reload and try again.");
	}

	private String invite() {
		return json(post(alice, api("/invitations")), "$.code");
	}

	private void join(RequestPostProcessor user) {
		assertThat(post(user, "/api/invitations/" + invite() + "/accept")).hasStatusOk();
	}

	private String aliceId() {
		return JsonPath.<List<String>>read(content(get(alice, api(""))), "$.members[?(@.me == true)].userId")
				.getFirst();
	}

	private String api(String path) {
		return "/api/households/" + household + path;
	}

	private MvcTestResult get(RequestPostProcessor user, String uri) {
		return mvc.get().uri(uri).with(user).exchange();
	}

	private MvcTestResult post(RequestPostProcessor user, String uri) {
		return mvc.post().uri(uri).with(user).exchange();
	}

	private MvcTestResult post(RequestPostProcessor user, String uri, String body) {
		return mvc.post().uri(uri).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult delete(RequestPostProcessor user, String uri) {
		return mvc.delete().uri(uri).with(user).exchange();
	}

	private static String json(MvcTestResult result, String path) {
		return JsonPath.read(content(result), path);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static RequestPostProcessor user(String subject, String name) {
		return jwt().jwt(token -> token.subject(subject).claim("name", name));
	}

}
