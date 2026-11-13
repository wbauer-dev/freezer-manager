package app.freezer.catalog.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import app.freezer.catalog.domain.FoodCategories;
import app.freezer.catalog.domain.FoodCategory;
import app.freezer.catalog.domain.FoodCategoryId;
import app.freezer.household.HouseholdId;

interface JdbcFoodCategories extends FoodCategories, Repository<FoodCategory, FoodCategoryId> {

	@Override
	@Query("select * from catalog.food_category where household_id = :householdId order by position, lower(name)")
	List<FoodCategory> findAllOfHousehold(HouseholdId householdId);

	@Override
	@Query("select * from catalog.food_category where household_id = :householdId and lower(name) = lower(:name)")
	Optional<FoodCategory> findByName(HouseholdId householdId, String name);

	@Override
	@Query("select coalesce(max(position), 0) + 1 from catalog.food_category where household_id = :householdId")
	int nextPosition(HouseholdId householdId);

	@Override
	@Modifying
	@Query("delete from catalog.food_category where household_id = :householdId")
	void deleteAllOfHousehold(HouseholdId householdId);

}
