package app.freezer.inventory.persistence;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import app.freezer.catalog.ProductId;
import app.freezer.household.HouseholdId;
import app.freezer.inventory.FrozenItemId;
import app.freezer.inventory.domain.CompartmentId;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.inventory.domain.FrozenItem;
import app.freezer.inventory.domain.FrozenItems;
import app.freezer.sharedkernel.LabelCode;

interface JdbcFrozenItems extends FrozenItems, Repository<FrozenItem, FrozenItemId> {

	@Override
	@Query("select * from inventory.frozen_item where product_id = :productId and state = 'IN_FREEZER'")
	List<FrozenItem> findInFreezerByProduct(ProductId productId);

	@Override
	@Query("""
			select exists (select 1 from inventory.frozen_item
			               where compartment_id = :compartmentId and state = 'IN_FREEZER')
			""")
	boolean anyInCompartment(CompartmentId compartmentId);

	@Override
	@Query("""
			select exists (select 1 from inventory.frozen_item
			               where freezer_id = :freezerId and state = 'IN_FREEZER')
			""")
	boolean anyInFreezer(FreezerId freezerId);

	@Override
	@Query("""
			select exists (select 1 from inventory.frozen_item
			               where household_id = :householdId and label_code = :labelCode and state = 'IN_FREEZER')
			""")
	boolean labelInUse(HouseholdId householdId, LabelCode labelCode);

}
