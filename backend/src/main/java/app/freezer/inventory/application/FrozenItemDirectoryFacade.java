package app.freezer.inventory.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemDirectory;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.ItemDescription;
import app.freezer.inventory.domain.FrozenItems;

/**
 * Implements the published query API. Other modules see {@link ItemDescription}, never the aggregate.
 */
@Service
@Transactional(readOnly = true)
class FrozenItemDirectoryFacade implements FrozenItemDirectory {

	private final FrozenItems items;

	FrozenItemDirectoryFacade(FrozenItems items) {
		this.items = items;
	}

	@Override
	public Optional<ItemDescription> describe(HouseholdId householdId, FrozenItemId itemId) {
		return items.findById(itemId)
				.filter(item -> item.belongsTo(householdId))
				.map(item -> new ItemDescription(item.getId(), item.getProductName(), item.getFrozenOn(),
						item.getBestBefore().date()));
	}

}
