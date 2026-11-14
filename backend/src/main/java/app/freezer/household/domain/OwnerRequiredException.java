package app.freezer.household.domain;

/**
 * Only owners manage the household: invite, remove members, make members owners.
 */
public class OwnerRequiredException extends RuntimeException {

	public OwnerRequiredException() {
		super("household.owner-required");
	}

}
