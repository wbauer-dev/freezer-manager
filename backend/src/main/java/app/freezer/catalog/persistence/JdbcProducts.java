package app.freezer.catalog.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import app.freezer.catalog.ProductId;
import app.freezer.catalog.domain.FoodCategoryId;
import app.freezer.catalog.domain.Product;
import app.freezer.catalog.domain.Products;
import app.freezer.household.HouseholdId;

interface JdbcProducts extends Products, Repository<Product, ProductId> {

	@Override
	@Query("select * from catalog.product where household_id = :householdId order by lower(name)")
	List<Product> findAllOfHousehold(HouseholdId householdId);

	@Override
	@Query("select * from catalog.product where household_id = :householdId and lower(name) = lower(:name)")
	Optional<Product> findByName(HouseholdId householdId, String name);

	@Override
	@Query("select exists (select 1 from catalog.product where category_id = :categoryId)")
	boolean anyInCategory(FoodCategoryId categoryId);

	@Override
	@Modifying
	@Query("delete from catalog.product where household_id = :householdId")
	void deleteAllOfHousehold(HouseholdId householdId);

}
