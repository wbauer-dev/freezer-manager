package app.freezer.media;

import static app.freezer.media.TestImages.png;
import static app.freezer.media.TestImages.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
import app.freezer.media.application.ImageProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MediaApiIntegrationTests {

	@Autowired
	MockMvcTester mvc;

	@MockitoSpyBean
	ImageProcessor processor;

	RequestPostProcessor alice;
	String household;

	@BeforeEach
	void setUp() {
		alice = user("alice-" + UUID.randomUUID());
		household = createHousehold(alice);
	}

	@Test
	void uploadedPhotoIsKeptInTwoSizes() {
		var upload = upload(alice, household, png(2000, 1500, false));
		assertThat(upload).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{ "width": 1600, "height": 1200 }
				""");
		var photo = photoUri(household, json(upload, "$.id"));

		var thumbnail = mvc.get().uri(photo).with(alice).exchange();
		assertThat(thumbnail).hasStatusOk().hasContentType(MediaType.IMAGE_JPEG)
				.headers().hasValue("Cache-Control", "max-age=31536000, private, immutable");
		assertThat(read(thumbnail.getResponse().getContentAsByteArray()).getWidth()).isEqualTo(400);

		var large = mvc.get().uri(photo + "?size=LARGE").with(alice).exchange();
		assertThat(read(large.getResponse().getContentAsByteArray()).getWidth()).isEqualTo(1600);
	}

	@Test
	void photosAreInvisibleOutsideTheHousehold() {
		var photoId = json(upload(alice, household, png(400, 300, false)), "$.id");
		var carol = user("carol-" + UUID.randomUUID());

		assertThat(mvc.get().uri(photoUri(household, photoId)).with(carol)).hasStatus(HttpStatus.NOT_FOUND);

		// A member of another household doesn't get it under their own household either.
		var bob = user("bob-" + UUID.randomUUID());
		var bobsHousehold = createHousehold(bob);
		assertThat(mvc.get().uri(photoUri(bobsHousehold, photoId)).with(bob)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri(photoUri(bobsHousehold, photoId) + "?size=LARGE").with(bob))
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri(photoUri(household, photoId)).with(bob)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsFilesThatAreNoPhotos() {
		assertThat(upload(alice, household, "<svg/>".getBytes(StandardCharsets.UTF_8)))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsEmptyFilesAsEmpty() {
		assertThat(upload(alice, household, new byte[0])).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
				.extractingPath("$.detail").isEqualTo("The uploaded file is empty.");
	}

	@Test
	void photosAreProcessedWithoutHoldingADatabaseTransaction() {
		var inTransaction = new AtomicBoolean(true);
		doAnswer(invocation -> {
			inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
			return invocation.callRealMethod();
		}).when(processor).process(any());

		assertThat(upload(alice, household, png(800, 600, false))).hasStatus(HttpStatus.CREATED);
		assertThat(inTransaction).isFalse();
	}

	@Test
	void frozenItemsShowAPhotoOfTheirOwnHousehold() {
		var photo = json(upload(alice, household, png(800, 600, false)), "$.id");
		var catalog = content(mvc.get().uri(api(household, "/products")).with(alice).exchange());
		List<String> products = JsonPath.read(catalog, "$[?(@.name == 'Peas')].id");
		var peas = products.getFirst();
		var kitchen = json(post(alice, api(household, "/freezers"),
				"{ \"name\": \"Kitchen\", \"compartments\": [ \"Top\" ] }"), "$.id");
		var top = json(mvc.get().uri(api(household, "/freezers")).with(alice).exchange(), "$[0].compartments[0].id");
		var freeze = """
				{ "productId": "%s", "freezerId": "%s", "compartmentId": "%s", "portions": 1, "frozenOn": "%s",
				  "photoId": %s }
				""";

		var item = post(alice, api(household, "/items"),
				freeze.formatted(peas, kitchen, top, LocalDate.now(), "\"" + photo + "\""));
		assertThat(item).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.photoId").isEqualTo(photo);
		assertThat(mvc.get().uri(api(household, "/eat-first")).with(alice)).bodyJson()
				.extractingPath("$[0].photoId").isEqualTo(photo);

		var withoutPhoto = json(post(alice, api(household, "/items"),
				freeze.formatted(peas, kitchen, top, LocalDate.now(), "null")), "$.id");
		var attachPhoto = api(household, "/items/" + withoutPhoto + "/photo");
		assertThat(post(alice, attachPhoto, "{ \"photoId\": \"" + photo + "\" }"))
				.hasStatusOk().bodyJson().extractingPath("$.photoId").isEqualTo(photo);

		var bob = user("bob-" + UUID.randomUUID());
		var bobsPhoto = json(upload(bob, createHousehold(bob), png(400, 300, false)), "$.id");
		assertThat(post(alice, attachPhoto, "{ \"photoId\": \"" + bobsPhoto + "\" }"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	private String createHousehold(RequestPostProcessor user) {
		return json(post(user, "/api/households", "{ \"name\": \"Home\" }"), "$.id");
	}

	private MvcTestResult upload(RequestPostProcessor user, String household, byte[] file) {
		return mvc.post().uri(api(household, "/photos")).with(user).multipart().file("file", file).exchange();
	}

	private MvcTestResult post(RequestPostProcessor user, String uri, String body) {
		return mvc.post().uri(uri).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private static String api(String household, String path) {
		return "/api/households/" + household + path;
	}

	private static String photoUri(String household, String photo) {
		return api(household, "/photos/" + photo);
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
