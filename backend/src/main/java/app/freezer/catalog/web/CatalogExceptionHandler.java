package app.freezer.catalog.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.catalog.application.CatalogNotFoundException;
import app.freezer.catalog.application.StaleVersionException;
import app.freezer.catalog.domain.CatalogRuleViolation;
import app.freezer.sharedkernel.Translatable;

/**
 * Translates the catalog's exceptions into problem details in the user's language. Problems reported with a message
 * code carry it as the property {@code code}, so that clients can tell them apart, e.g. a change to a version that is
 * no longer current ({@code catalog.product.changed}) from a name that is taken.
 */
@RestControllerAdvice(basePackageClasses = CatalogExceptionHandler.class)
class CatalogExceptionHandler {

	private final MessageSource messages;

	CatalogExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(CatalogNotFoundException.class)
	ProblemDetail notFound(CatalogNotFoundException e, Locale locale) {
		return problem(HttpStatus.NOT_FOUND, e, locale);
	}

	@ExceptionHandler(CatalogRuleViolation.class)
	ProblemDetail ruleViolated(CatalogRuleViolation e, Locale locale) {
		return problem(HttpStatus.CONFLICT, e, locale);
	}

	/** Another member changed the category or product since the member saw it. */
	@ExceptionHandler(StaleVersionException.class)
	ProblemDetail stale(StaleVersionException e, Locale locale) {
		return problem(HttpStatus.CONFLICT, e, locale);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e, Locale locale) {
		return problem(HttpStatus.BAD_REQUEST, e, locale);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail changedConcurrently(Locale locale) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				messages.getMessage("changed-concurrently", null, locale));
	}

	/** Two members chose the same name at the same moment: the unique index decides. */
	@ExceptionHandler(DuplicateKeyException.class)
	ProblemDetail duplicate(Locale locale) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, messages.getMessage("name-taken", null, locale));
	}

	/**
	 * Another member removed a category (or started the catalog over) while a product was added to it: the foreign key
	 * decides. {@link DuplicateKeyException}, a special case, is handled above.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail conflict(Locale locale) {
		return changedConcurrently(locale);
	}

	private ProblemDetail problem(HttpStatus status, RuntimeException e, Locale locale) {
		var arguments = e instanceof Translatable translatable ? translatable.getArguments() : null;
		var text = e.getMessage() == null ? null : messages.getMessage(e.getMessage(), arguments, null, locale);
		if (text == null) {
			// Not a message code, e.g. an IllegalArgumentException from a library.
			return ProblemDetail.forStatusAndDetail(status, e.getMessage());
		}
		var problem = ProblemDetail.forStatusAndDetail(status, text);
		problem.setProperty("code", e.getMessage());
		return problem;
	}

}
