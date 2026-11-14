package app.freezer.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import app.freezer.TestcontainersConfiguration;

/**
 * The household's catalog through the REST API, with PostgreSQL. Each test works in a fresh household.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiIntegrationTests {

	@Autowired
	MockMvcTester mvc;

	RequestPostProcessor alice;
	String household;

	@BeforeEach
	void setUpHousehold() {
		alice = user("alice-" + UUID.randomUUID());
		household = json(send(mvc.post().uri("/api/households"), alice, "{ \"name\": \"Alice's flat\" }"), "$.id");
	}

	@Test
	void householdStartsWithTheSystemDefaultsInTheLanguageOfTheFirstMemberToOpenIt() {
		assertThat(mvc.get().uri(api("/food-categories")).with(alice).header("Accept-Language", "de-AT,de;q=0.9"))
				.hasStatusOk().bodyJson().extractingPath("$[0]").isEqualTo(java.util.Map.of("id",
						json(mvc.get().uri(api("/food-categories")).with(alice).exchange(), "$[0].id"), "name",
						"Rind & Lamm", "emoji", "🥩", "shelfLifeMonths", 10));

		// Started once; other languages see the household's catalog as it is.
		assertThat(mvc.get().uri(api("/products")).with(alice).header("Accept-Language", "en")).bodyJson()
				.extractingPath("$[*].name").asArray().contains("Erbsen", "Rinderhackfleisch").doesNotContain("Peas");
		assertThat(mvc.get().uri(api("/catalog")).with(alice)).bodyJson()
				.extractingPath("$.startedWith").isEqualTo("de");
	}

	@Test
	void unknownLanguagesStartWithEnglishDefaults() {
		assertThat(mvc.get().uri(api("/food-categories")).with(alice).header("Accept-Language", "fr")).bodyJson()
				.extractingPath("$[0].name").isEqualTo("Beef & lamb");
	}

	@Test
	void offersTheLanguagesOfTheSystemDefaults() {
		assertThat(mvc.get().uri("/api/catalog/languages").with(alice)).bodyJson().isEqualTo("""
				[ { "code": "de", "name": "Deutsch" }, { "code": "en", "name": "English" } ]
				""");
	}

	@Test
	void everyMemberCanChangeCategoriesAndProducts() {
		var game = post("/food-categories", """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				""");
		assertThat(game).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				""");
		var gameId = json(game, "$.id");
		assertThat(mvc.get().uri(api("/food-categories")).with(alice)).bodyJson()
				.extractingPath("$[-1:].name").asArray().containsExactly("Game");

		var venison = post("/products", """
				{ "name": "Venison", "categoryId": "%s" }
				""".formatted(gameId));
		assertThat(venison).hasStatus(HttpStatus.CREATED);
		var venisonId = json(venison, "$.id");

		assertThat(put("/food-categories/" + gameId, """
				{ "name": "Game & venison", "emoji": "🦌", "shelfLifeMonths": 10 }
				""")).hasStatusOk().bodyJson().extractingPath("$.shelfLifeMonths").isEqualTo(10);

		var poultry = categoryNamed("Poultry");
		assertThat(put("/products/" + venisonId, """
				{ "name": "Wild duck", "categoryId": "%s" }
				""".formatted(poultry))).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "name": "Wild duck", "categoryId": "%s" }
				""".formatted(poultry));

		assertThat(delete("/food-categories/" + gameId)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(delete("/products/" + venisonId)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri(api("/products")).with(alice)).bodyJson()
				.extractingPath("$[*].name").asArray().doesNotContain("Venison", "Wild duck");
	}

	@Test
	void namesAreUniqueWithinTheHousehold() {
		var vegetables = categoryNamed("Vegetables");

		assertThat(post("/products", """
				{ "name": "PEAS", "categoryId": "%s" }
				""".formatted(vegetables))).hasStatus(HttpStatus.CONFLICT).bodyJson()
				.extractingPath("$.detail").isEqualTo("There already is a product named “PEAS”.");
		assertThat(put("/food-categories/" + vegetables, """
				{ "name": "fruit", "emoji": "🥦", "shelfLifeMonths": 12 }
				""")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void explainsProblemsInTheUsersLanguage() {
		var vegetables = categoryNamed("Vegetables");

		assertThat(mvc.post().uri(api("/products")).with(alice).header("Accept-Language", "de")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{ "name": "peas", "categoryId": "%s" }
						""".formatted(vegetables))).bodyJson()
				.extractingPath("$.detail").isEqualTo("Es gibt schon ein Produkt namens „peas“.");
		assertThat(mvc.post().uri(api("/products")).with(alice).header("Accept-Language", "de")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{ "name": "", "categoryId": "%s" }
						""".formatted(vegetables))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
				.extractingPath("$.detail").isEqualTo("Einige Angaben fehlen oder sind ungültig.");
	}

	@Test
	void onlyEmptyCategoriesCanBeRemoved() {
		assertThat(delete("/food-categories/" + categoryNamed("Vegetables"))).hasStatus(HttpStatus.CONFLICT)
				.bodyJson().extractingPath("$.detail")
				.isEqualTo("“Vegetables” still has products. Move or remove them first.");
	}

	@Test
	void rejectsInvalidCategoriesAndProducts() {
		assertThat(post("/food-categories", """
				{ "name": "Game", "emoji": "", "shelfLifeMonths": 9 }
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/food-categories", """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 61 }
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/products", """
				{ "name": "Venison", "categoryId": "%s" }
				""".formatted(UUID.randomUUID()))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void resetStartsOverEmptyOrWithTheSystemDefaults() {
		categoryNamed("Vegetables");

		assertThat(post("/catalog/reset", """
				{ "mode": "EMPTY" }
				""")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri(api("/food-categories")).with(alice)).bodyJson().isEqualTo("[]");
		assertThat(mvc.get().uri(api("/products")).with(alice)).bodyJson().isEqualTo("[]");
		assertThat(mvc.get().uri(api("/catalog")).with(alice)).bodyJson()
				.extractingPath("$.startedWith").isNull();

		assertThat(post("/catalog/reset", """
				{ "mode": "DEFAULTS", "language": "de" }
				""")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri(api("/food-categories")).with(alice)).bodyJson()
				.extractingPath("$[*].name").asArray().hasSize(15).contains("Gemüse");
	}

	@Test
	void catalogIsInvisibleOutsideTheHousehold() {
		var vegetables = categoryNamed("Vegetables");
		var carol = user("carol-" + UUID.randomUUID());
		var carolsHousehold = json(send(mvc.post().uri("/api/households"), carol, "{ \"name\": \"Carol's\" }"), "$.id");

		for (var path : List.of("/food-categories", "/products", "/catalog")) {
			assertThat(mvc.get().uri(api(path)).with(carol)).hasStatus(HttpStatus.NOT_FOUND);
		}
		assertThat(send(mvc.put().uri("/api/households/" + carolsHousehold + "/food-categories/" + vegetables), carol,
				"{ \"name\": \"Mine\", \"emoji\": \"🥦\", \"shelfLifeMonths\": 12 }")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(send(mvc.post().uri("/api/households/" + carolsHousehold + "/products"), carol,
				"{ \"name\": \"Mine\", \"categoryId\": \"" + vegetables + "\" }")).hasStatus(HttpStatus.NOT_FOUND);
	}

	/** A member of another household uses this household's ids under the URLs of their own household. */
	@Test
	void idsOfAnotherHouseholdAreUnknownInOnesOwn() {
		var vegetables = categoryNamed("Vegetables");
		List<String> peas = JsonPath.read(content(mvc.get().uri(api("/products")).with(alice).exchange()),
				"$[?(@.name == 'Peas')].id");
		var carol = user("carol-" + UUID.randomUUID());
		var carols = "/api/households/"
				+ json(send(mvc.post().uri("/api/households"), carol, "{ \"name\": \"Carol's\" }"), "$.id");

		assertThat(send(mvc.put().uri(carols + "/products/" + peas.getFirst()), carol,
				"{ \"name\": \"Mine\", \"categoryId\": \"" + vegetables + "\" }")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.delete().uri(carols + "/products/" + peas.getFirst()).with(carol))
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.delete().uri(carols + "/food-categories/" + vegetables).with(carol))
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(send(mvc.put().uri(carols + "/food-categories/" + vegetables), carol,
				"{ \"name\": \"Mine\", \"emoji\": \"🥦\", \"shelfLifeMonths\": 12 }")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(send(mvc.post().uri(carols + "/products"), carol,
				"{ \"name\": \"Mine\", \"categoryId\": \"" + vegetables + "\" }")).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(mvc.get().uri(api("/products")).with(alice)).bodyJson()
				.extractingPath("$[*].name").asArray().contains("Peas").doesNotContain("Mine");
		assertThat(mvc.get().uri(api("/food-categories")).with(alice)).bodyJson()
				.extractingPath("$[*].name").asArray().contains("Vegetables").doesNotContain("Mine");
	}

	private String categoryNamed(String name) {
		List<String> ids = JsonPath.read(content(mvc.get().uri(api("/food-categories")).with(alice).exchange()),
				"$[?(@.name == '" + name + "')].id");
		return ids.getFirst();
	}

	private String api(String path) {
		return "/api/households/" + household + path;
	}

	private MvcTestResult post(String path, String body) {
		return send(mvc.post().uri(api(path)), alice, body);
	}

	private MvcTestResult put(String path, String body) {
		return send(mvc.put().uri(api(path)), alice, body);
	}

	private MvcTestResult delete(String path) {
		return mvc.delete().uri(api(path)).with(alice).exchange();
	}

	private static MvcTestResult send(MockMvcTester.MockMvcRequestBuilder request, RequestPostProcessor user,
			String body) {
		return request.with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
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

	private static RequestPostProcessor user(String subject) {
		return jwt().jwt(token -> token.subject(subject).claim("name", subject));
	}

}
