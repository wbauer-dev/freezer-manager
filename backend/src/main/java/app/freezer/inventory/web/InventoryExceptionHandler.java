package app.freezer.inventory.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.inventory.application.InventoryNotFoundException;
import app.freezer.inventory.domain.InventoryRuleViolation;
import app.freezer.sharedkernel.Translatable;

/**
 * Translates the inventory's exceptions into problem details in the user's language. Applies to this module's
 * controllers only.
 */
@RestControllerAdvice(basePackageClasses = InventoryExceptionHandler.class)
class InventoryExceptionHandler {

	private final MessageSource messages;

	InventoryExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(InventoryNotFoundException.class)
	ProblemDetail notFound(InventoryNotFoundException e, Locale locale) {
		return problem(HttpStatus.NOT_FOUND, e, locale);
	}

	@ExceptionHandler(InventoryRuleViolation.class)
	ProblemDetail ruleViolated(InventoryRuleViolation e, Locale locale) {
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

	@ExceptionHandler(DuplicateKeyException.class)
	ProblemDetail duplicate(Locale locale) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, messages.getMessage("name-taken", null, locale));
	}

	private ProblemDetail problem(HttpStatus status, RuntimeException e, Locale locale) {
		var arguments = e instanceof Translatable translatable ? translatable.getArguments() : null;
		return ProblemDetail.forStatusAndDetail(status,
				messages.getMessage(e.getMessage(), arguments, e.getMessage(), locale));
	}

}
