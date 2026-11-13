package app.freezer.catalog.templates;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import app.freezer.catalog.application.CatalogTemplate;
import app.freezer.catalog.application.CatalogTemplates;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads {@code catalog/templates/<language>.json} once at start-up. A new file is a new language.
 */
@Component
class JsonCatalogTemplates implements CatalogTemplates {

	private static final String FALLBACK = "en";

	private final Map<String, CatalogTemplate> byLanguage = new LinkedHashMap<>();

	JsonCatalogTemplates() {
		var json = JsonMapper.builder().build();
		try {
			for (var resource : new PathMatchingResourcePatternResolver()
					.getResources("classpath*:catalog/templates/*.json")) {
				try (var input = resource.getInputStream()) {
					var template = json.readValue(input, CatalogTemplate.class);
					byLanguage.put(template.language(), template);
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException("Catalog templates could not be read", e);
		}
		if (!byLanguage.containsKey(FALLBACK)) {
			throw new IllegalStateException("The English catalog template is missing");
		}
	}

	@Override
	public List<CatalogTemplate> all() {
		return byLanguage.values().stream().sorted(Comparator.comparing(CatalogTemplate::name)).toList();
	}

	@Override
	public CatalogTemplate inLanguage(String language) {
		return byLanguage.getOrDefault(language, byLanguage.get(FALLBACK));
	}

}
