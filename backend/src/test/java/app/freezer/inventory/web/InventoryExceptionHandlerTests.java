package app.freezer.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

/**
 * A unique index that rejects a change tells the member what clashed.
 */
class InventoryExceptionHandlerTests {

	final InventoryExceptionHandler handler = new InventoryExceptionHandler(messages());

	@Test
	void labelIndexMeansTheLabelIsOnAnotherItem() {
		var problem = handler.duplicate(rejectedBy("frozen_item_label_in_freezer"), Locale.ENGLISH);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getDetail()).isEqualTo("This label is already on another item in your freezer.");
	}

	@Test
	void freezerNameIndexMeansTheNameIsTaken() {
		var problem = handler.duplicate(rejectedBy("freezer_name_per_household"), Locale.GERMAN);

		assertThat(problem.getDetail()).isEqualTo("Dieser Name ist bereits vergeben.");
	}

	@Test
	void otherIndexesMeanAConcurrentChange() {
		var problem = handler.duplicate(rejectedBy("compartment_id_key"), Locale.ENGLISH);

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(problem.getDetail()).startsWith("Someone else changed this at the same time.");
	}

	/** What Spring's translation of PostgreSQL's error 23505 looks like. */
	private static DuplicateKeyException rejectedBy(String index) {
		return new DuplicateKeyException("PreparedStatementCallback; SQL [insert ...]",
				new SQLException("""
						ERROR: duplicate key value violates unique constraint "%s"
						  Detail: Key (...)=(...) already exists.""".formatted(index), "23505"));
	}

	private static ResourceBundleMessageSource messages() {
		var messages = new ResourceBundleMessageSource();
		messages.setBasenames("i18n/common", "i18n/inventory");
		messages.setDefaultEncoding("UTF-8");
		messages.setFallbackToSystemLocale(false);
		return messages;
	}

}
