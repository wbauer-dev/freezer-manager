package app.freezer.household.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.sharedkernel.Translatable;

/**
 * Translates the household module's exceptions into problem details in the user's language.
 */
@RestControllerAdvice(basePackageClasses = HouseholdExceptionHandler.class)
class HouseholdExceptionHandler {

	private final MessageSource messages;

	HouseholdExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e, Locale locale) {
		return problem(HttpStatus.BAD_REQUEST, e, locale);
	}

	private ProblemDetail problem(HttpStatus status, RuntimeException e, Locale locale) {
		var arguments = e instanceof Translatable translatable ? translatable.getArguments() : null;
		return ProblemDetail.forStatusAndDetail(status,
				messages.getMessage(e.getMessage(), arguments, e.getMessage(), locale));
	}

}
