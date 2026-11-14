package app.freezer.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.LocalDate;
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
 * Inventory and Catalog through the REST API, with PostgreSQL. Each test works in a fresh household.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InventoryApiIntegrationTests {

	static final LocalDate TODAY = LocalDate.now();

	@Autowired
	MockMvcTester mvc;

	RequestPostProcessor alice;
	String household;
	String mincedMeat;
	String peas;
	String kitchen;
	String top;
	String bottom;

	@BeforeEach
	void setUpHouseholdWithFreezerAndProducts() {
		alice = user("alice-" + UUID.randomUUID(), "Alice");
		household = id(post("/api/households", """
				{ "name": "Alice's flat" }
				"""));
		// A new household starts with the system defaults.
		mincedMeat = productNamed("Minced beef");
		peas = productNamed("Peas");
		kitchen = id(post(api("/freezers"), """
				{ "name": "Kitchen", "compartments": [ "Top", "Bottom" ] }
				"""));
		var freezers = mvc.get().uri(api("/freezers")).with(alice).exchange();
		top = json(freezers, "$[0].compartments[0].id");
		bottom = json(freezers, "$[0].compartments[1].id");
	}

	@Test
	void itemGoesThroughItsLifecycle() {
		var frozenOn = TODAY.minusMonths(3).plusDays(5);
		var item = post(api("/items"), freezeRequest(mincedMeat, top, 2, frozenOn));

		assertThat(item).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{ "productName": "Minced beef", "state": "IN_FREEZER", "remainingPortions": 2,
				  "bestBefore": "%s", "bestBeforeOverridden": false, "expiryStatus": "EXPIRING_SOON",
				  "freezerName": "Kitchen", "compartmentName": "Top", "canUndoTakeOut": false }
				""".formatted(frozenOn.plusMonths(3)));
		var itemUri = api("/items/" + id(item));

		assertThat(post(itemUri + "/take-out", """
				{ "portions": 1 }
				""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "remainingPortions": 1, "canUndoTakeOut": true,
				  "withdrawals": [ { "portions": 1, "takenBy": "Alice" } ] }
				""");

		assertThat(post(itemUri + "/undo-take-out", "")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "remainingPortions": 2, "withdrawals": [] }
				""");

		assertThat(post(itemUri + "/take-out", """
				{ "portions": 2 }
				""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "state": "CONSUMED", "remainingPortions": 0 }
				""");

		assertThat(post(itemUri + "/take-out", """
				{ "portions": 1 }
				""")).hasStatus(HttpStatus.CONFLICT).bodyJson().isLenientlyEqualTo("""
				{ "detail": "This item is no longer in the freezer." }
				""");
	}

	@Test
	void eatFirstListShowsItemsInTheFreezerByBestBeforeDate() {
		post(api("/items"), freezeRequest(peas, bottom, 3, TODAY));
		post(api("/items"), freezeRequest(mincedMeat, top, 1, TODAY));
		var discarded = id(post(api("/items"), freezeRequest(mincedMeat, top, 1, TODAY)));
		post(api("/items/" + discarded + "/discard"), """
				{ "reason": "FREEZER_BURN" }
				""");

		var eatFirst = mvc.get().uri(api("/eat-first")).with(alice).exchange();

		assertThat(eatFirst).hasStatusOk().bodyJson().extractingPath("$[*].productName")
				.isEqualTo(List.of("Minced beef", "Peas"));
		assertThat(eatFirst).bodyJson().isLenientlyEqualTo("""
				[ { "productName": "Minced beef", "remainingPortions": 1, "bestBefore": "%s", "expiryStatus": "FRESH",
				    "freezerId": "%s", "freezerName": "Kitchen", "compartmentId": "%s", "compartmentName": "Top" },
				  { "productName": "Peas", "remainingPortions": 3, "bestBefore": "%s", "expiryStatus": "FRESH",
				    "freezerId": "%s", "freezerName": "Kitchen", "compartmentId": "%s", "compartmentName": "Bottom" } ]
				""".formatted(TODAY.plusMonths(3), kitchen, top, TODAY.plusMonths(12), kitchen, bottom));
	}

	@Test
	void bestBeforeDateCanBeOverridden() {
		var bestBefore = TODAY.plusWeeks(1);
		var request = """
				{ "productId": "%s", "freezerId": "%s", "compartmentId": "%s", "portions": 1,
				  "frozenOn": "%s", "bestBefore": "%s", "gramsPerPortion": 400, "note": "Sunday leftovers" }
				""".formatted(mincedMeat, kitchen, top, TODAY, bestBefore);

		assertThat(post(api("/items"), request)).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{ "bestBefore": "%s", "bestBeforeOverridden": true, "gramsPerPortion": 400, "note": "Sunday leftovers" }
				""".formatted(bestBefore));
	}

	@Test
	void onlyEmptyCompartmentsCanBeRemoved() {
		post(api("/items"), freezeRequest(peas, top, 1, TODAY));

		assertThat(mvc.delete().uri(api("/freezers/" + kitchen + "/compartments/" + top)).with(alice))
				.hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.delete().uri(api("/freezers/" + kitchen + "/compartments/" + bottom)).with(alice))
				.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri(api("/freezers")).with(alice)).bodyJson().isLenientlyEqualTo("""
				[ { "name": "Kitchen", "compartments": [ { "name": "Top", "itemCount": 1 } ] } ]
				""");
	}

	@Test
	void onlyEmptyFreezersCanBeRemovedAndTheirItemsKeepTheirHistory() {
		var item = id(post(api("/items"), freezeRequest(peas, top, 1, TODAY)));

		assertThat(mvc.delete().uri(api("/freezers/" + kitchen)).with(alice)).hasStatus(HttpStatus.CONFLICT)
				.bodyJson().extractingPath("$.detail").isEqualTo("Only empty freezers can be removed.");

		post(api("/items/" + item + "/take-out"), """
				{ "portions": 1 }
				""");
		assertThat(mvc.delete().uri(api("/freezers/" + kitchen)).with(alice)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.get().uri(api("/freezers")).with(alice)).bodyJson().isEqualTo("[]");
		assertThat(mvc.get().uri(api("/items/" + item)).with(alice)).hasStatusOk().bodyJson()
				.extractingPath("$.freezerName").isEqualTo("Kitchen");
		assertThat(mvc.delete().uri(api("/freezers/" + kitchen)).with(alice)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(api("/freezers"), """
				{ "name": "Kitchen", "compartments": [ "Main" ] }
				""")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void anEatenItemDoesNotComeBackIntoARemovedCompartment() {
		var itemUri = api("/items/" + id(post(api("/items"), freezeRequest(peas, bottom, 1, TODAY))));
		post(itemUri + "/take-out", """
				{ "portions": 1 }
				""");
		assertThat(mvc.delete().uri(api("/freezers/" + kitchen + "/compartments/" + bottom)).with(alice))
				.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.get().uri(itemUri).with(alice)).bodyJson().isLenientlyEqualTo("""
				{ "state": "CONSUMED", "canUndoTakeOut": false }
				""");
		assertThat(post(itemUri + "/undo-take-out", "")).hasStatus(HttpStatus.CONFLICT).bodyJson()
				.extractingPath("$.detail").isEqualTo(
						"This item cannot go back into the freezer: its compartment or the freezer has been removed.");
		assertThat(mvc.get().uri(itemUri).with(alice)).bodyJson().extractingPath("$.state").isEqualTo("CONSUMED");
	}

	@Test
	void anEatenItemDoesNotComeBackIntoARemovedFreezer() {
		var itemUri = api("/items/" + id(post(api("/items"), freezeRequest(peas, top, 1, TODAY))));
		post(itemUri + "/take-out", """
				{ "portions": 1 }
				""");
		assertThat(mvc.delete().uri(api("/freezers/" + kitchen)).with(alice)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.get().uri(itemUri).with(alice)).bodyJson().extractingPath("$.canUndoTakeOut").isEqualTo(false);
		assertThat(post(itemUri + "/undo-take-out", "")).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.get().uri(api("/eat-first")).with(alice)).bodyJson().isEqualTo("[]");
	}

	@Test
	void compartmentsCanBeRearranged() {
		assertThat(post(api("/freezers/" + kitchen + "/rearrange-compartments"), """
				{ "compartmentIds": [ "%s", "%s" ] }
				""".formatted(bottom, top))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.get().uri(api("/freezers")).with(alice)).bodyJson()
				.extractingPath("$[0].compartments[*].name").isEqualTo(List.of("Bottom", "Top"));
		assertThat(post(api("/freezers/" + kitchen + "/rearrange-compartments"), """
				{ "compartmentIds": [ "%s" ] }
				""".formatted(top))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsInvalidCommands() {
		assertThat(post(api("/items"), freezeRequest(peas, top, 0, TODAY))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(api("/items"), freezeRequest(UUID.randomUUID().toString(), top, 1, TODAY)))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(api("/items"), freezeRequest(peas, UUID.randomUUID().toString(), 1, TODAY)))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(api("/freezers"), """
				{ "name": "kitchen", "compartments": [ "Main" ] }
				""")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void inventoryIsInvisibleOutsideTheHousehold() {
		var item = id(post(api("/items"), freezeRequest(peas, top, 1, TODAY)));
		var carol = user("carol-" + UUID.randomUUID(), "Carol");

		for (var uri : List.of(api("/eat-first"), api("/freezers"), api("/products"), api("/items/" + item))) {
			assertThat(mvc.get().uri(uri).with(carol)).hasStatus(HttpStatus.NOT_FOUND);
		}
		assertThat(mvc.post().uri(api("/items/" + item + "/take-out")).with(carol)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"portions\": 1 }"))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void itemsInTheFreezerFollowARenamedProduct() {
		var inFreezer = id(post(api("/items"), freezeRequest(peas, top, 1, TODAY)));
		var eaten = id(post(api("/items"), freezeRequest(peas, top, 1, TODAY)));
		post(api("/items/" + eaten + "/take-out"), """
				{ "portions": 1 }
				""");
		List<String> categories = JsonPath.read(content(mvc.get().uri(api("/products")).with(alice).exchange()),
				"$[?(@.id == '" + peas + "')].categoryId");
		var vegetables = categories.getFirst();

		assertThat(mvc.put().uri(api("/products/" + peas)).with(alice).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Garden peas", "categoryId": "%s" }
						""".formatted(vegetables))).hasStatusOk();

		await().untilAsserted(() -> assertThat(mvc.get().uri(api("/items/" + inFreezer)).with(alice)).bodyJson()
				.extractingPath("$.productName").isEqualTo("Garden peas"));
		assertThat(mvc.get().uri(api("/items/" + eaten)).with(alice)).bodyJson()
				.extractingPath("$.productName").isEqualTo("Peas");
	}

	private String freezeRequest(String product, String compartment, int portions, LocalDate frozenOn) {
		return """
				{ "productId": "%s", "freezerId": "%s", "compartmentId": "%s", "portions": %d, "frozenOn": "%s" }
				""".formatted(product, kitchen, compartment, portions, frozenOn);
	}

	private String productNamed(String name) {
		List<String> ids = JsonPath.read(content(mvc.get().uri(api("/products")).with(alice).exchange()),
				"$[?(@.name == '" + name + "')].id");
		return ids.getFirst();
	}

	private String api(String path) {
		return "/api/households/" + household + path;
	}

	private MvcTestResult post(String uri, String body) {
		return mvc.post().uri(uri).with(alice).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static String id(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return json(result, "$.id");
	}

	private static String json(MvcTestResult result, String path) {
		return JsonPath.read(content(result), path);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static RequestPostProcessor user(String subject, String name) {
		return jwt().jwt(token -> token.subject(subject).claim("name", name));
	}

}
