package app.freezer.shopping.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.sharedkernel.Translatable;
import app.freezer.shopping.domain.ShoppingRuleViolation;

/**
 * Translates the shopping list's exceptions into problem details in the user's language.
 */
@RestControllerAdvice(basePackageClasses = ShoppingExceptionHandler.class)
class ShoppingExceptionHandler {

	private final MessageSource messages;

	ShoppingExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(ShoppingRuleViolation.class)
	ProblemDetail ruleViolated(ShoppingRuleViolation e, Locale locale) {
		return problem(HttpStatus.CONFLICT, e, locale);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e, Locale locale) {
		return problem(HttpStatus.BAD_REQUEST, e, locale);
	}

	/** Only after a change collided several times in a row; a single collision is applied again. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail changedConcurrently(Locale locale) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				messages.getMessage("changed-concurrently", null, locale));
	}

	private ProblemDetail problem(HttpStatus status, RuntimeException e, Locale locale) {
		var arguments = e instanceof Translatable translatable ? translatable.getArguments() : null;
		return ProblemDetail.forStatusAndDetail(status,
				messages.getMessage(e.getMessage(), arguments, e.getMessage(), locale));
	}

}
