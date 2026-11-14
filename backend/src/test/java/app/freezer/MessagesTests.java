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
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the translations: every language has every message, and every message code in the code has a text.
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

	@Test
	void everyMessageCodeHasAText() throws IOException {
		var known = new TreeSet<String>();
		for (var bundle : BUNDLES) {
			known.addAll(load(bundle).stringPropertyNames());
		}
		var literal = Pattern
				.compile("\"((?:household|catalog|inventory|labeling|media|shopping)(?:\\.[a-z-]+){2,})\"");
		var used = new TreeSet<>(COMPOSED_CODES);
		try (var sources = Files.walk(Path.of("src/main/java"))) {
			for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
				var matcher = literal.matcher(Files.readString(source));
				while (matcher.find()) {
					used.add(matcher.group(1));
				}
			}
		}
		assertThat(known).containsAll(used);
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
