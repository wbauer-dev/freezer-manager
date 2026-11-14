package app.freezer.labeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import app.freezer.TestcontainersConfiguration;
import app.freezer.labeling.domain.LabelSheets;

/**
 * Inventory and Labeling working together through events, observed via the REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LabelingApiIntegrationTests {

	static final LocalDate TODAY = LocalDate.now();

	@Autowired
	MockMvcTester mvc;

	@MockitoSpyBean
	LabelSheets sheets;

	RequestPostProcessor alice;
	String household;
	String peas;
	String kitchen;
	String top;

	@BeforeEach
	void setUp() {
		alice = user("alice-" + UUID.randomUUID());
		household = json(post("/api/households", "{ \"name\": \"Alice's flat\" }"), "$.id");
		// A new household starts with the system defaults.
		List<String> products = JsonPath.read(content(mvc.get().uri(api("/products")).with(alice).exchange()),
				"$[?(@.name == 'Peas')].id");
		peas = products.getFirst();
		kitchen = json(post(api("/freezers"), "{ \"name\": \"Kitchen\", \"compartments\": [ \"Top\" ] }"), "$.id");
		top = json(mvc.get().uri(api("/freezers")).with(alice).exchange(), "$[0].compartments[0].id");
	}

	@Test
	void frozenItemGetsAPrintableLabelThatIsUsedUpWhenEaten() {
		var item = post(api("/items"), freezeRequest(2, null));
		var itemId = json(item, "$.id");
		var code = json(item, "$.labelCode");

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(get(api("/label-sheet")))
				.bodyJson().extractingPath("$.queue[*].code").isEqualTo(List.of(code)));
		assertThat(get(api("/label-sheet"))).bodyJson().extractingPath("$.queue[0]").isEqualTo(java.util.Map.of(
				"code", code, "layout", "QR_AND_CONTENT", "title", "Peas", "frozenOn", TODAY.toString(),
				"bestBefore", TODAY.plusMonths(12).toString()));
		assertThat(get("/api/labels/" + code)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{ "kind": "ONE_TIME", "state": "ATTACHED", "householdId": "%s", "itemId": "%s" }
				""".formatted(household, itemId));

		var pdf = mvc.post().uri(api("/label-sheet/print")).with(alice).exchange();
		assertThat(pdf).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
		assertThat(pdf.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
		assertThat(get(api("/label-sheet"))).bodyJson().isLenientlyEqualTo("""
				{ "columns": 3, "rows": 8, "usedPositions": [ 0 ], "nextPosition": 1, "queue": [] }
				""");

		post(api("/items/" + itemId + "/take-out"), "{ \"portions\": 2 }");
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(get("/api/labels/" + code))
				.bodyJson().extractingPath("$.state").isEqualTo("USED_UP"));
	}

	@Test
	void persistentLabelGoesFromContainerToContainer() {
		var codes = mvc.post().uri(api("/labels/persistent")).with(alice)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"count\": 2 }").exchange();
		assertThat(codes).hasStatus(HttpStatus.CREATED);
		var code = json(codes, "$[0]");
		assertThat(get("/api/labels/" + code)).bodyJson().isLenientlyEqualTo("""
				{ "kind": "PERSISTENT", "state": "FREE" }
				""");
		assertThat(get(api("/label-sheet"))).bodyJson().extractingPath("$.queue[*].layout")
				.isEqualTo(List.of("QR_ONLY", "QR_ONLY"));
		assertThat(get(api("/labels/stock"))).bodyJson().isLenientlyEqualTo("""
				{ "freePersistent": 2, "persistentInUse": 0 }
				""");
		var other = json(codes, "$[1]");
		var bothFree = Stream.of(code, other).sorted().toList();
		assertThat(get(api("/labels/free"))).bodyJson().extractingPath("$[*].code").isEqualTo(bothFree);
		assertThat(get(api("/labels/free"))).bodyJson().extractingPath("$[*].lastContents")
				.isEqualTo(Arrays.asList(null, null));

		var itemId = json(post(api("/items"), freezeRequest(1, code.toLowerCase(Locale.ROOT))), "$.id");
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(get("/api/labels/" + code))
				.bodyJson().extractingPath("$.state").isEqualTo("ATTACHED"));
		assertThat(post(api("/items"), freezeRequest(1, code))).hasStatus(HttpStatus.CONFLICT);
		assertThat(get(api("/labels/free"))).bodyJson().extractingPath("$[*].code").isEqualTo(List.of(other));

		assertThat(post(api("/label-sheet/queue"), """
				{ "code": "%s", "layout": "CONTENT_ONLY", "itemId": "%s" }
				""".formatted(code, itemId))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get(api("/label-sheet"))).bodyJson().extractingPath("$.queue[2]").isEqualTo(java.util.Map.of(
				"code", code, "layout", "CONTENT_ONLY", "title", "Peas", "frozenOn", TODAY.toString(),
				"bestBefore", TODAY.plusMonths(12).toString()));

		post(api("/items/" + itemId + "/discard"), "{ \"reason\": \"OTHER\" }");
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(get("/api/labels/" + code))
				.bodyJson().extractingPath("$.state").isEqualTo("FREE"));
		// Free again, and it remembers what it held, so members can tell their containers apart.
		var free = get(api("/labels/free"));
		assertThat(free).bodyJson().extractingPath("$[*].code").isEqualTo(bothFree);
		assertThat(free).bodyJson().extractingPath("$[?(@.code == '%s')].lastContents".formatted(code))
				.isEqualTo(List.of("Peas"));
	}

	@Test
	void labelsAreUnknownOutsideTheHousehold() {
		var code = json(post(api("/items"), freezeRequest(1, null)), "$.labelCode");
		await().atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> assertThat(get("/api/labels/" + code)).hasStatusOk());

		assertThat(mvc.get().uri("/api/labels/" + code).with(user("carol-" + UUID.randomUUID())))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void codesThatCannotBeLabelsAreUnknownLabels() {
		assertThat(get("/api/labels/K7Q2")).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.detail")
				.isEqualTo("Unknown label.");
		assertThat(post(api("/label-sheet/queue"), """
				{ "code": "not a code", "layout": "QR_ONLY" }
				""")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.delete().uri(api("/label-sheet/queue/K7Q2-M9XU?layout=QR_ONLY")).with(alice))
				.hasStatus(HttpStatus.NOT_FOUND);

		// Typed when freezing, the code is explained in the member's language.
		assertThat(mvc.post().uri(api("/items")).with(alice).header(HttpHeaders.ACCEPT_LANGUAGE, "de")
				.contentType(MediaType.APPLICATION_JSON).content(freezeRequest(1, "K7Q2")))
				.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.detail")
				.isEqualTo("Der Code eines Etiketts hat 8 Zeichen, z. B. K7Q2-M9XA.");
	}

	@Test
	void aSheetThatKeepsChangingIsAConflict() {
		var newSheet = api("/label-sheet/new-sheet");
		for (var collision : List.of(new OptimisticLockingFailureException("collided"),
				new DuplicateKeyException("created at the same time"))) {
			doThrow(collision).when(sheets).save(any());

			assertThat(post(newSheet, "")).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.detail")
					.isEqualTo("Someone else changed this at the same time. Please reload and try again.");
		}
	}

	private String freezeRequest(int portions, String labelCode) {
		return """
				{ "productId": "%s", "freezerId": "%s", "compartmentId": "%s", "portions": %d, "frozenOn": "%s",
				  "labelCode": %s }
				""".formatted(peas, kitchen, top, portions, TODAY,
						labelCode == null ? "null" : "\"" + labelCode + "\"");
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

	private static RequestPostProcessor user(String subject) {
		return jwt().jwt(token -> token.subject(subject).claim("name", subject));
	}

}
