package app.freezer.household.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.household.HouseholdNotFoundException;

/**
 * Applies to the controllers of all modules: whoever is not a member of a household gets a 404.
 */
@RestControllerAdvice
class HouseholdNotFoundHandler {

	private final MessageSource messages;

	HouseholdNotFoundHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(HouseholdNotFoundException.class)
	ProblemDetail householdNotFound(Locale locale) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				messages.getMessage("household.not-found", null, locale));
	}

}
