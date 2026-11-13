package app.freezer.labeling.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.labeling.application.UnknownLabelException;
import app.freezer.labeling.domain.LabelingRuleViolation;
import app.freezer.sharedkernel.Translatable;

/**
 * Translates the labeling module's exceptions into problem details in the user's language.
 */
@RestControllerAdvice(basePackageClasses = LabelingExceptionHandler.class)
class LabelingExceptionHandler {

	private final MessageSource messages;

	LabelingExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(UnknownLabelException.class)
	ProblemDetail unknownLabel(UnknownLabelException e, Locale locale) {
		return problem(HttpStatus.NOT_FOUND, e, locale);
	}

	@ExceptionHandler(LabelingRuleViolation.class)
	ProblemDetail ruleViolated(LabelingRuleViolation e, Locale locale) {
		return problem(HttpStatus.CONFLICT, e, locale);
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
