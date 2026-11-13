package app.freezer.labeling.persistence;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.LabelState;
import app.freezer.labeling.domain.Labels;
import app.freezer.sharedkernel.LabelCode;

interface JdbcLabels extends Labels, Repository<Label, LabelCode> {

	@Override
	@Query("""
			select count(*) from labeling.label
			where household_id = :householdId and kind = 'PERSISTENT' and state = :state
			""")
	int countPersistent(HouseholdId householdId, LabelState state);

}
