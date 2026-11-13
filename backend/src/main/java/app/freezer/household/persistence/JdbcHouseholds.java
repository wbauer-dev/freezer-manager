package app.freezer.household.persistence;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.household.UserId;
import app.freezer.household.domain.Household;
import app.freezer.household.domain.Households;

/**
 * Spring Data JDBC implements the domain's {@link Households} interface: {@code save} and {@code findById} are routed
 * to the generic repository implementation, the remaining queries are declared here.
 */
interface JdbcHouseholds extends Households, Repository<Household, HouseholdId> {

	@Override
	@Query("""
			select h.* from household.household h
			where exists (select 1 from household.member m where m.household_id = h.id and m.user_id = :userId)
			order by h.name
			""")
	List<Household> findAllWithMember(UserId userId);

}
