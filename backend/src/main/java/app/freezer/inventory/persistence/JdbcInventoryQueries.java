package app.freezer.inventory.persistence;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.application.EatFirstEntry;
import app.freezer.inventory.application.InventoryQueries;
import app.freezer.inventory.domain.BestBefore;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.media.PhotoId;

@Component
class JdbcInventoryQueries implements InventoryQueries {

	private final JdbcClient jdbc;

	JdbcInventoryQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<EatFirstEntry> eatFirst(HouseholdId householdId, LocalDate today) {
		return jdbc.sql("""
				select i.id, i.product_id, i.photo_id, i.product_name, i.note, i.portions_remaining,
				       i.grams_per_portion, i.frozen_on, i.best_before_date, i.best_before_overridden,
				       i.freezer_id, f.name as freezer_name, i.compartment_id, c.name as compartment_name
				from inventory.frozen_item i
				join inventory.freezer f on f.id = i.freezer_id
				left join inventory.compartment c on c.id = i.compartment_id
				where i.household_id = :householdId and i.state = 'IN_FREEZER'
				order by i.best_before_date, i.frozen_on, lower(i.product_name)
				""")
				.param("householdId", householdId.id())
				.query((row, number) -> {
					// The expiry rule stays in the domain, even for read models.
					var bestBefore = new BestBefore(row.getObject("best_before_date", LocalDate.class),
							row.getBoolean("best_before_overridden"));
					return new EatFirstEntry(FrozenItemId.of(row.getObject("id", UUID.class)),
							ProductId.of(row.getObject("product_id", UUID.class)),
							Optional.ofNullable(row.getObject("photo_id", UUID.class)).map(PhotoId::of).orElse(null),
							row.getString("product_name"), row.getString("note"), row.getInt("portions_remaining"),
							row.getObject("grams_per_portion", Integer.class),
							row.getObject("frozen_on", LocalDate.class), bestBefore.date(), bestBefore.statusOn(today),
							FreezerId.of(row.getObject("freezer_id", UUID.class)), row.getString("freezer_name"),
							CompartmentId.of(row.getObject("compartment_id", UUID.class)),
							row.getString("compartment_name"));
				})
				.list();
	}

	@Override
	public Map<CompartmentId, Integer> itemCountsByCompartment(HouseholdId householdId) {
		var counts = new HashMap<CompartmentId, Integer>();
		jdbc.sql("""
				select compartment_id, count(*) as items
				from inventory.frozen_item
				where household_id = :householdId and state = 'IN_FREEZER'
				group by compartment_id
				""")
				.param("householdId", householdId.id())
				.query(row -> {
					counts.put(CompartmentId.of(row.getObject("compartment_id", UUID.class)), row.getInt("items"));
				});
		return counts;
	}

}
