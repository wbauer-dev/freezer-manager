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
		var game = json(send(mvc.post().uri(api(household, "/food-categories")), """
				{ "name": "Game", "emoji": "🦌", "shelfLifeMonths": 9 }
				"""), "$.id");
		var categoryId = FoodCategoryId.of(UUID.fromString(game));
		var beforeRemoval = categories.findById(categoryId).orElseThrow();
		assertThat(mvc.delete().uri(api(household, "/food-categories/" + game)).with(alice))
				.hasStatus(HttpStatus.NO_CONTENT);
		// Adding the product still finds the category: another member removes it a moment later.
		doReturn(Optional.of(beforeRemoval)).when(categories).findById(categoryId);

		assertThat(send(mvc.post().uri(api(household, "/products")), """
				{ "name": "Venison", "categoryId": "%s" }
				""".formatted(game))).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.detail")
				.isEqualTo("Someone else changed this at the same time. Please reload and try again.");
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

	private static String json(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

}
