package app.freezer.labeling.persistence;

import org.springframework.data.repository.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.labeling.domain.LabelSheet;
import app.freezer.labeling.domain.LabelSheets;

interface JdbcLabelSheets extends LabelSheets, Repository<LabelSheet, HouseholdId> {
}
