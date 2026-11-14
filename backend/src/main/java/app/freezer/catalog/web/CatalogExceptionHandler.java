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
import app.freezer.catalog.domain.CatalogRuleViolation;
import app.freezer.sharedkernel.Translatable;

/**
 * Translates the catalog's exceptions into problem details in the user's language.
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
		return ProblemDetail.forStatusAndDetail(status,
				messages.getMessage(e.getMessage(), arguments, e.getMessage(), locale));
	}

}
