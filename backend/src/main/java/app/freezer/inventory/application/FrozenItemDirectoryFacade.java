package app.freezer.inventory.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
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
	private final JdbcClient jdbc;

	FrozenItemDirectoryFacade(FrozenItems items, JdbcClient jdbc) {
		this.items = items;
		this.jdbc = jdbc;
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
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
				select id, product_name, frozen_on, best_before_date from inventory.frozen_item
				where household_id = :householdId and id in (:itemIds)
				""")
				.param("householdId", householdId.id())
				.param("itemIds", itemIds.stream().map(FrozenItemId::id).distinct().toList())
				.query((row, number) -> new ItemDescription(FrozenItemId.of(row.getObject("id", UUID.class)),
						row.getString("product_name"), row.getObject("frozen_on", LocalDate.class),
						row.getObject("best_before_date", LocalDate.class)))
				.list()
				.stream()
				.collect(Collectors.toMap(ItemDescription::itemId, Function.identity()));
	}

}
