package app.freezer.inventory.application;

import java.util.Collection;
import java.util.Map;
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
	private final InventoryQueries queries;

	FrozenItemDirectoryFacade(FrozenItems items, InventoryQueries queries) {
		this.items = items;
		this.queries = queries;
	}

	@Override
	public Optional<ItemDescription> describe(HouseholdId householdId, FrozenItemId itemId) {
		return items.findById(itemId)
				.filter(item -> item.belongsTo(householdId))
				.map(item -> new ItemDescription(item.getId(), item.getProductName(), item.getFrozenOn(),
						item.getBestBefore().date()));
	}

	/**
	 * A read model in one query. Loading each aggregate would cost two queries per item (the item and its
	 * withdrawals), and the freeze page asks for the last item of every free label.
	 */
	@Override
	public Map<FrozenItemId, ItemDescription> describeAll(HouseholdId householdId,
			Collection<FrozenItemId> itemIds) {
		return queries.descriptions(householdId, itemIds);
	}

}
