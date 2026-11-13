package app.freezer.catalog.persistence;

import org.springframework.data.repository.Repository;

import app.freezer.catalog.domain.HouseholdCatalog;
import app.freezer.catalog.domain.HouseholdCatalogs;
import app.freezer.household.HouseholdId;

interface JdbcHouseholdCatalogs extends HouseholdCatalogs, Repository<HouseholdCatalog, HouseholdId> {
}
