package app.freezer.catalog.domain;

import java.time.Period;

import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import app.freezer.household.HouseholdId;
import app.freezer.sharedkernel.InvalidValueException;

/**
 * A grouping of products with an emoji and a recommended freezer shelf life, e.g. "🍔 Minced meat: 3 months". Each
 * household has its own categories and may change them; the system defaults are only a starting point.
 */
@Table(schema = "catalog", name = "food_category")
public class FoodCategory implements AggregateRoot<FoodCategory, FoodCategoryId> {

	public static final int MAX_SHELF_LIFE_MONTHS = 60;
	public static final int MAX_EMOJI_LENGTH = 16;

	@Identity
	private final FoodCategoryId id;
	private final HouseholdId householdId;
	private String name;
	private String emoji;
	private int shelfLifeMonths;
	private final int position;
	@Version
	private Long version;

	FoodCategory(FoodCategoryId id, HouseholdId householdId, String name, String emoji, int shelfLifeMonths,
			int position, Long version) {
		this.id = id;
		this.householdId = householdId;
		this.name = name;
		this.emoji = emoji;
		this.shelfLifeMonths = shelfLifeMonths;
		this.position = position;
		this.version = version;
	}

	public static FoodCategory define(HouseholdId householdId, String name, String emoji, int shelfLifeMonths,
			int position) {
		return new FoodCategory(FoodCategoryId.random(), householdId, Names.valid(name, "catalog.category"),
				validEmoji(emoji), validShelfLife(shelfLifeMonths), position, null);
	}

	/**
	 * Changes name, emoji and shelf life. A new shelf life applies to food frozen from now on; items already in the
	 * freezer keep the best-before date they got when they were frozen.
	 */
	public void edit(String name, String emoji, int shelfLifeMonths) {
		this.name = Names.valid(name, "catalog.category");
		this.emoji = validEmoji(emoji);
		this.shelfLifeMonths = validShelfLife(shelfLifeMonths);
	}

	public boolean belongsTo(HouseholdId householdId) {
		return this.householdId.equals(householdId);
	}

	@Override
	public FoodCategoryId getId() {
		return id;
	}

	public HouseholdId getHouseholdId() {
		return householdId;
	}

	public String getName() {
		return name;
	}

	public String getEmoji() {
		return emoji;
	}

	public int getShelfLifeMonths() {
		return shelfLifeMonths;
	}

	public Period shelfLife() {
		return Period.ofMonths(shelfLifeMonths);
	}

	private static String validEmoji(String emoji) {
		var trimmed = emoji == null ? "" : emoji.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_EMOJI_LENGTH) {
			throw new IllegalArgumentException("catalog.category.emoji-invalid");
		}
		return trimmed;
	}

	private static int validShelfLife(int months) {
		if (months < 1 || months > MAX_SHELF_LIFE_MONTHS) {
			throw new InvalidValueException("catalog.category.shelf-life-invalid", MAX_SHELF_LIFE_MONTHS);
		}
		return months;
	}

}
