package app.freezer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The security filter chain on a real server. MockMvc does not perform the servlet container's error dispatch to
 * {@code /error}, so these tests send real HTTP requests.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, SecurityIntegrationTests.FailingController.class })
class SecurityIntegrationTests {

	static final String TOKEN = "alices-token";

	@LocalServerPort
	int port;

	@MockitoBean
	JwtDecoder tokens;

	HttpClient http;

	@BeforeEach
	void setUp() {
		http = HttpClient.newHttpClient();
		when(tokens.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "none").subject("alice")
				.claim("name", "Alice").build());
	}

	@AfterEach
	void tearDown() {
		http.close();
	}

	/** Spring Security also authorizes the error dispatch; denying it reported every failure as 403. */
	@Test
	void anUnexpectedFailureIsReportedAsServerError() throws Exception {
		assertThat(get("/api/test/failure", TOKEN).statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
	}

	@Test
	void requestsWithoutTokenDoNotReachTheApplication() throws Exception {
		assertThat(get("/api/test/failure", null).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
	}

	/** Only the error dispatch is permitted, not a request for the error page. */
	@Test
	void theErrorPageCannotBeRequestedDirectly() throws Exception {
		assertThat(get("/error", null).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(get("/error", TOKEN).statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
	}

	private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return http.send(request.build(), BodyHandlers.ofString());
	}

	/** Fails like a bug would: with an exception no controller advice handles. */
	@RestController
	static class FailingController {

		@GetMapping("/api/test/failure")
		void fail() {
			throw new IllegalStateException("A bug");
		}

	}

}
