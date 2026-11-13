package app.freezer.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import app.freezer.TestcontainersConfiguration;

/**
 * End-to-end through REST, application service, aggregate, Spring Data JDBC and PostgreSQL (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HouseholdApiIntegrationTests {

	@Autowired
	MockMvcTester mvc;

	@Test
	void foundedHouseholdIsListedWithFounderAsOwner() {
		var alice = user("alice-1", "Alice");

		var created = mvc.post().uri("/api/households").with(alice)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Alice's flat" }
						""")
				.exchange();

		assertThat(created).hasStatus(HttpStatus.CREATED)
				.bodyJson().extractingPath("$.members[0].role").isEqualTo("OWNER");
		var location = created.getResponse().getHeader("Location");

		assertThat(mvc.get().uri("/api/households").with(alice))
				.hasStatusOk()
				.bodyJson().isLenientlyEqualTo("""
						[ { "name": "Alice's flat", "myRole": "OWNER", "memberCount": 1 } ]
						""");

		assertThat(mvc.get().uri(location).with(alice))
				.hasStatusOk()
				.bodyJson().extractingPath("$.members[0].displayName").isEqualTo("Alice");
	}

	@Test
	void householdsAreInvisibleToNonMembers() {
		var bob = user("bob-1", "Bob");
		var carol = user("carol-1", "Carol");

		var created = mvc.post().uri("/api/households").with(bob)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Bob's flat" }
						""")
				.exchange();
		var location = created.getResponse().getHeader("Location");

		assertThat(mvc.get().uri(location).with(carol)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/households").with(carol)).hasStatusOk().bodyJson().isEqualTo("[]");
	}

	@Test
	void rejectsBlankName() {
		assertThat(mvc.post().uri("/api/households").with(user("dave-1", "Dave"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": " " }
						"""))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void requiresAuthentication() {
		assertThat(mvc.get().uri("/api/households")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private static RequestPostProcessor user(String subject, String name) {
		return jwt().jwt(token -> token.subject(subject).claim("name", name));
	}

}
