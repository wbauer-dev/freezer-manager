package app.freezer.inventory.application;

/**
 * A freezer or item does not exist in the member's household. The message is a message code.
 */
public class InventoryNotFoundException extends RuntimeException {

	public InventoryNotFoundException(String code) {
		super(code);
	}

}
