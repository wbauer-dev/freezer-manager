package app.freezer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the translations: every language has every message with the same placeholders, and every message code in the
 * code has a text.
 */
class MessagesTests {

	static final List<String> BUNDLES = List.of("common", "household", "catalog", "inventory", "labeling", "media",
			"shopping");
	static final List<String> LANGUAGES = List.of("de");

	/** Codes put together at runtime, e.g. {@code what + ".name-blank"}. */
	static final Set<String> COMPOSED_CODES = Set.of("inventory.freezer.name-blank", "inventory.freezer.name-too-long",
			"inventory.compartment.name-blank", "inventory.compartment.name-too-long", "catalog.category.name-blank",
			"catalog.category.name-too-long", "catalog.product.name-blank", "catalog.product.name-too-long",
			"labeling.label.attached", "labeling.label.used-up");

	/** Messages shared by all modules ({@code common.properties}); their codes have no module prefix. */
	static final Set<String> COMMON_CODES = Set.of("changed-concurrently", "name-taken");

	/** A message code in the code, e.g. {@code "household.not-found"} or {@code "inventory.item.not-in-freezer"}. */
	static final Pattern MODULE_CODE = Pattern
			.compile("\"((?:household|catalog|inventory|labeling|media|shopping)(?:\\.[a-z-]+)+)\"");

	/** A code looked up directly, e.g. {@code messages.getMessage("changed-concurrently", ...)}. */
	static final Pattern LOOKED_UP_CODE = Pattern.compile("getMessage\\(\\s*\"([^\"]+)\"");

	/** The argument index of a placeholder: {@code {0}}, {@code {0,number,integer}}, {@code {0,choice,...}}. */
	static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

	static Stream<String> languages() {
		return LANGUAGES.stream();
	}

	@ParameterizedTest
	@MethodSource("languages")
	void everyLanguageHasEveryMessage(String language) throws IOException {
		for (var bundle : BUNDLES) {
			assertThat(load(bundle + "_" + language).stringPropertyNames())
					.as("%s in %s", bundle, language)
					.containsExactlyInAnyOrderElementsOf(load(bundle).stringPropertyNames());
		}
	}

	@ParameterizedTest
	@MethodSource("languages")
	void everyLanguageHasTheSamePlaceholders(String language) throws IOException {
		var different = new TreeMap<String, String>();
		for (var bundle : BUNDLES) {
			var english = load(bundle);
			var translated = load(bundle + "_" + language);
			for (var code : english.stringPropertyNames()) {
				var text = translated.getProperty(code);
				if (text != null && !placeholders(text).equals(placeholders(english.getProperty(code)))) {
					different.put(code, text);
				}
			}
		}
		assertThat(different).as("messages in %s with other placeholders than in English", language).isEmpty();
	}

	@Test
	void everyMessageCodeHasAText() throws IOException {
		var known = new TreeSet<String>();
		for (var bundle : BUNDLES) {
			known.addAll(load(bundle).stringPropertyNames());
		}
		var used = new TreeSet<>(COMPOSED_CODES);
		used.addAll(COMMON_CODES);
		try (var sources = Files.walk(Path.of("src/main/java"))) {
			for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
				var code = Files.readString(source);
				for (var pattern : List.of(MODULE_CODE, LOOKED_UP_CODE)) {
					var matcher = pattern.matcher(code);
					while (matcher.find()) {
						used.add(matcher.group(1));
					}
				}
			}
		}
		// Not codes but what composed codes are made of, e.g. "catalog.product" in what + ".name-blank"
		used.removeIf(literal -> COMPOSED_CODES.stream().anyMatch(composed -> composed.startsWith(literal + ".")));
		assertThat(known).containsAll(used);
	}

	private static Set<String> placeholders(String text) {
		var indexes = new TreeSet<String>();
		var matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			indexes.add(matcher.group(1));
		}
		return indexes;
	}

	private static Properties load(String name) throws IOException {
		var properties = new Properties();
		try (var in = MessagesTests.class.getResourceAsStream("/i18n/" + name + ".properties")) {
			assertThat(in).as(name).isNotNull();
			properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return properties;
	}

}
