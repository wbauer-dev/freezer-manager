package app.freezer.catalog.application;

import java.util.List;

/**
 * The system default catalogs. Adding a language means adding a template, nothing else.
 */
public interface CatalogTemplates {

	List<CatalogTemplate> all();

	/** The defaults in this language, or in English if there are none for it. */
	CatalogTemplate inLanguage(String language);

}
