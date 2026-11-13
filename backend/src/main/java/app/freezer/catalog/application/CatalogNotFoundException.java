package app.freezer.catalog.application;

/**
 * A category or product does not exist in the member's household. The message is a message code.
 */
public class CatalogNotFoundException extends RuntimeException {

	public CatalogNotFoundException(String code) {
		super(code);
	}

}
