package app.freezer.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import app.freezer.TestcontainersConfiguration;

/**
 * The household's shopping list through the REST API, with the real Catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShoppingApiIntegrationTests {

	@Autowired
	MockMvcTester mvc;

	RequestPostProcessor alice;
	String household;
	String peas;
	String vegetables;

	@BeforeEach
	void setUp() {
		alice = user("alice-" + UUID.randomUUID(), "Alice");
		household = json(post("/api/households", "{ \"name\": \"Alice's flat\" }"), "$.id");
		// A new household starts with the system defaults.
		var products = content(get(api("/products")));
		peas = JsonPath.<List<String>>read(products, "$[?(@.name == 'Peas')].id").getFirst();
		vegetables = JsonPath.<List<String>>read(products, "$[?(@.name == 'Peas')].categoryId").getFirst();
	}

	@Test
	void membersFillTheListAndClearTheBasketAfterShopping() {
		assertThat(get(api("/shopping-list"))).hasStatusOk().bodyJson().isEqualTo("{ \"entries\": [] }");

		post(api("/shopping-list/entries"), "{ \"productId\": \"%s\", \"quantity\": \"500 g\" }".formatted(peas));
		post(api("/shopping-list/entries"), "{ \"name\": \"Milk\" }");
		var list = post(api("/shopping-list/entries"),
				"{ \"productId\": \"%s\", \"quantity\": \"1 kg\" }".formatted(peas));

		assertThat(list).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "entries": [
				  { "productId": "%s", "name": "Peas", "quantity": "500 g + 1 kg", "source": "MANUAL",
				    "addedBy": "Alice", "checked": false, "checkedBy": null },
				  { "productId": null, "name": "Milk", "quantity": null, "checked": false }
				] }
				""".formatted(peas));

		var milk = json(list, "$.entries[1].id");
		assertThat(post(api("/shopping-list/entries/" + milk + "/check"), "")).hasStatusOk().bodyJson()
				.isLenientlyEqualTo("""
						{ "entries": [
						  { "name": "Peas", "checked": false },
						  { "id": "%s", "name": "Milk", "checked": true, "checkedBy": "Alice" }
						] }
						""".formatted(milk));
		assertThat(post(api("/shopping-list/clear-checked"), "")).hasStatusOk().bodyJson()
				.extractingPath("$.entries[*].name").isEqualTo(List.of("Peas"));
	}

	@Test
	void untickingAndRemovingEntries() {
		var milk = json(post(api("/shopping-list/entries"), "{ \"name\": \"Milk\" }"), "$.entries[0].id");
		post(api("/shopping-list/entries/" + milk + "/check"), "");

		assertThat(post(api("/shopping-list/entries/" + milk + "/uncheck"), "")).bodyJson()
				.extractingPath("$.entries[0].checked").isEqualTo(false);
		assertThat(mvc.delete().uri(api("/shopping-list/entries/" + milk)).with(alice)).hasStatusOk().bodyJson()
				.extractingPath("$.entries").asArray().isEmpty();
		// Removing it again is fine: another member may have been quicker.
		assertThat(mvc.delete().uri(api("/shopping-list/entries/" + milk)).with(alice)).hasStatusOk();
	}

	@Test
	void productsKeepTheirCatalogName() {
		post(api("/shopping-list/entries"), "{ \"productId\": \"%s\" }".formatted(peas));

		assertThat(mvc.put().uri(api("/products/" + peas)).with(alice).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"Garden peas\", \"categoryId\": \"%s\" }".formatted(vegetables)))
				.hasStatusOk();

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(get(api("/shopping-list"))).bodyJson()
				.extractingPath("$.entries[0].name").isEqualTo("Garden peas"));
	}

	@Test
	void rejectsInvalidEntriesInTheMembersLanguage() {
		assertThat(post(api("/shopping-list/entries"), "{ \"name\": \" \" }")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(api("/shopping-list/entries"), "{ \"name\": \"%s\" }".formatted("x".repeat(61))))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(api("/shopping-list/entries"), "{ \"productId\": \"%s\" }".formatted(UUID.randomUUID())))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri(api("/shopping-list/entries")).with(alice).header("Accept-Language", "de")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"productId\": \"%s\", \"name\": \"Peas\" }".formatted(peas)))
				.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.detail")
				.isEqualTo("Wähle ein Produkt oder gib einen Namen ein, nicht beides.");
	}

	@Test
	void theListIsInvisibleOutsideTheHousehold() {
		var milk = json(post(api("/shopping-list/entries"), "{ \"name\": \"Milk\" }"), "$.entries[0].id");
		var carol = user("carol-" + UUID.randomUUID(), "Carol");

		assertThat(mvc.get().uri(api("/shopping-list")).with(carol)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri(api("/shopping-list/entries/" + milk + "/check")).with(carol))
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(api("/shopping-list"))).bodyJson().extractingPath("$.entries[0].checked").isEqualTo(false);
	}

	private String api(String path) {
		return "/api/households/" + household + path;
	}

	private MvcTestResult get(String uri) {
		return mvc.get().uri(uri).with(alice).exchange();
	}

	private MvcTestResult post(String uri, String body) {
		return mvc.post().uri(uri).with(alice).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
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
