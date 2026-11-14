package app.freezer.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jayway.jsonpath.JsonPath;

import app.freezer.TestcontainersConfiguration;
import app.freezer.catalog.application.CatalogSetup;
import app.freezer.catalog.domain.FoodCategories;
import app.freezer.catalog.domain.FoodCategoryId;

/**
 * Members using the same catalog at the same moment, through the REST API with PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogConcurrencyIntegrationTests {

	@Autowired
	MockMvcTester mvc;

	@MockitoSpyBean
	FoodCategories categories;

	@MockitoSpyBean
	CatalogSetup setup;

	RequestPostProcessor alice;

	@BeforeEach
	void setUp() {
		alice = jwt().jwt(token -> token.subject("alice-" + UUID.randomUUID()).claim("name", "Alice"));
	}

	/**
	 * Starting a catalog takes a transaction (and connection) of its own. Inside another transaction, each request
	 * would hold two connections, and many members opening their catalogs for the first time could exhaust the pool.
	 */
	@Test
	void theCatalogIsStartedOutsideOtherTransactions() {
		var startedWithinTransaction = new ArrayList<Boolean>();
		doAnswer(invocation -> {
			startedWithinTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
			return invocation.callRealMethod();
		}).when(setup).ensureStarted(any(), any());

		assertThat(mvc.get().uri(api(newHousehold(), "/food-categories")).with(alice)).hasStatusOk();
		assertThat(mvc.get().uri(api(newHousehold(), "/products")).with(alice)).hasStatusOk();
		assertThat(mvc.get().uri(api(newHousehold(), "/catalog")).with(alice)).hasStatusOk();
		assertThat(send(mvc.post().uri(api(newHousehold(), "/food-categories")), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				""")).hasStatus(HttpStatus.CREATED);

		assertThat(startedWithinTransaction).containsExactly(false, false, false, false);
	}

	@Test
	void addingAProductToACategoryRemovedInTheMeantimeIsAConflict() {
		var household = newHousehold();
		String game = json(send(mvc.post().uri(api(household, "/food-categories")), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				"""), "$.id");
		var categoryId = FoodCategoryId.of(UUID.fromString(game));
		var beforeRemoval = categories.findById(categoryId).orElseThrow();
		var version = beforeRemoval.getVersion();
		assertThat(mvc.delete().uri(api(household, "/food-categories/" + game + "?version=" + version)).with(alice))
				.hasStatus(HttpStatus.NO_CONTENT);
		// Adding the product still finds the category: another member removes it a moment later.
		doReturn(Optional.of(beforeRemoval)).when(categories).findById(categoryId);

		assertThat(send(mvc.post().uri(api(household, "/products")), """
				{ "name": "Venison", "categoryId": "%s" }
				""".formatted(game))).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.detail")
				.isEqualTo("Someone else changed this at the same time. Please reload and try again.");
	}

	/** Alice and Bob both see the same version of the venison; Bob saves first, Alice's change would overwrite his. */
	@Test
	void changingAFoodSomeoneElseChangedInTheMeantimeIsAConflict() {
		var household = newHousehold();
		var game = defineCategory(household);
		var venison = send(mvc.post().uri(api(household, "/products")), """
				{ "name": "Venison", "categoryId": "%s" }
				""".formatted(game));
		String id = json(venison, "$.id");
		int seen = json(venison, "$.version");

		var bobs = send(mvc.put().uri(api(household, "/products/" + id)), """
				{ "name": "Deer", "categoryId": "%s", "version": %d }
				""".formatted(game, seen));
		assertThat(bobs).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(seen + 1);

		var alices = send(mvc.put().uri(api(household, "/products/" + id)), """
				{ "name": "Roe deer", "categoryId": "%s", "version": %d }
				""".formatted(game, seen));
		assertThat(alices).hasStatus(HttpStatus.CONFLICT).bodyJson().isLenientlyEqualTo("""
				{ "detail": "Someone else changed this food in the meantime. Here is the current version.",
				  "code": "catalog.product.changed" }
				""");
		assertThat(mvc.delete().uri(api(household, "/products/" + id + "?version=" + seen)).with(alice)
				.header("Accept-Language", "de")).hasStatus(HttpStatus.CONFLICT).bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Jemand anderer hat dieses Lebensmittel inzwischen geändert. "
						+ "Hier siehst du den aktuellen Stand.");
		assertThat(mvc.get().uri(api(household, "/products")).with(alice)).bodyJson()
				.extractingPath("$[?(@.id == '%s')].name".formatted(id)).asArray().containsExactly("Deer");

		assertThat(mvc.delete().uri(api(household, "/products/" + id + "?version=" + (seen + 1))).with(alice))
				.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void changingACategorySomeoneElseChangedInTheMeantimeIsAConflict() {
		var household = newHousehold();
		var game = send(mvc.post().uri(api(household, "/food-categories")), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				""");
		String id = json(game, "$.id");
		int seen = json(game, "$.version");

		assertThat(send(mvc.put().uri(api(household, "/food-categories/" + id)), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 12, "version": %d }
				""".formatted(seen))).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(seen + 1);

		assertThat(send(mvc.put().uri(api(household, "/food-categories/" + id)), """
				{ "name": "Venison", "emoji": "🦌", "shelfLifeMonths": 6, "version": %d }
				""".formatted(seen))).hasStatus(HttpStatus.CONFLICT).bodyJson().isLenientlyEqualTo("""
				{ "detail": "Someone else changed this category in the meantime. Here is the current version.",
				  "code": "catalog.category.changed" }
				""");
		assertThat(mvc.delete().uri(api(household, "/food-categories/" + id + "?version=" + seen)).with(alice)
				.header("Accept-Language", "de")).hasStatus(HttpStatus.CONFLICT).bodyJson()
				.extractingPath("$.detail")
				.isEqualTo("Jemand anderer hat diese Kategorie inzwischen geändert. "
						+ "Hier siehst du den aktuellen Stand.");

		assertThat(mvc.delete().uri(api(household, "/food-categories/" + id + "?version=" + (seen + 1)))
				.with(alice)).hasStatus(HttpStatus.NO_CONTENT);
	}

	/** Every change says which version it was made to; otherwise it could overwrite what its sender never saw. */
	@Test
	void changesWithoutAVersionAreRejected() {
		var household = newHousehold();
		var game = defineCategory(household);
		String venison = json(send(mvc.post().uri(api(household, "/products")), """
				{ "name": "Venison", "categoryId": "%s" }
				""".formatted(game)), "$.id");

		assertThat(send(mvc.put().uri(api(household, "/products/" + venison)), """
				{ "name": "Deer", "categoryId": "%s" }
				""".formatted(game))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(send(mvc.put().uri(api(household, "/food-categories/" + game)), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 12 }
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.delete().uri(api(household, "/products/" + venison)).with(alice)
				.header("Accept-Language", "de")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
				.extractingPath("$.detail").isEqualTo("Einige Angaben fehlen oder sind ungültig.");
		assertThat(mvc.delete().uri(api(household, "/food-categories/" + game)).with(alice))
				.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.detail")
				.isEqualTo("Some values are missing or invalid.");
	}

	private String defineCategory(String household) {
		return json(send(mvc.post().uri(api(household, "/food-categories")), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				"""), "$.id");
	}

	private String newHousehold() {
		return json(send(mvc.post().uri("/api/households"), "{ \"name\": \"Alice's flat\" }"), "$.id");
	}

	private static String api(String household, String path) {
		return "/api/households/" + household + path;
	}

	private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder request, String body) {
		return request.with(alice).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static <T> T json(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

}
