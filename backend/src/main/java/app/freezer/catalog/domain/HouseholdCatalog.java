package app.freezer.catalog.domain;

import java.time.Instant;
import java.util.Optional;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;

/**
 * A household's catalog as a whole: when it was started, and with which system defaults. The categories and products
 * themselves are aggregates of their own.
 */
@Table(schema = "catalog", name = "household_catalog")
public class HouseholdCatalog implements AggregateRoot<HouseholdCatalog, HouseholdId> {

	@Identity
	private final HouseholdId householdId;
	/** Language of the system defaults the catalog started with; {@code null} if it started empty. */
	private String language;
	private Instant startedAt;
	@Version
	private Long version;

	HouseholdCatalog(HouseholdId householdId, String language, Instant startedAt, Long version) {
		this.householdId = householdId;
		this.language = language;
		this.startedAt = startedAt;
		this.version = version;
	}

	/** @param language of the system defaults, or {@code null} for an empty catalog */
	public static HouseholdCatalog start(HouseholdId householdId, String language, Instant now) {
		return new HouseholdCatalog(householdId, language, now, null);
	}

	/** Starts over — the caller removes the old categories and products. */
	public void restart(String language, Instant now) {
		this.language = language;
		this.startedAt = now;
	}

	@Override
	public HouseholdId getId() {
		return householdId;
	}

	public Optional<String> getLanguage() {
		return Optional.ofNullable(language);
	}

	public Instant getStartedAt() {
		return startedAt;
	}

}
