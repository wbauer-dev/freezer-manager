package app.freezer.inventory.persistence;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.inventory.domain.Freezer;
import app.freezer.inventory.domain.FreezerId;
import app.freezer.inventory.domain.Freezers;

interface JdbcFreezers extends Freezers, Repository<Freezer, FreezerId> {

	@Override
	@Query("""
			select * from inventory.freezer
			where household_id = :householdId and removed_at is null
			order by lower(name)
			""")
	List<Freezer> findAllOfHousehold(HouseholdId householdId);

	@Override
	@Query("""
			select exists (select 1 from inventory.freezer
			               where household_id = :householdId and lower(name) = lower(:name) and removed_at is null)
			""")
	boolean existsWithName(HouseholdId householdId, String name);

}
