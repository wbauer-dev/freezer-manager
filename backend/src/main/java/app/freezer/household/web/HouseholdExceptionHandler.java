package app.freezer.household.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.household.domain.HouseholdRuleViolation;
import app.freezer.household.domain.InvalidInvitationException;
import app.freezer.household.domain.OwnerRequiredException;
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

	/** Unknown, expired, used and revoked invitations look the same. */
	@ExceptionHandler(InvalidInvitationException.class)
	ProblemDetail invalidInvitation(InvalidInvitationException e, Locale locale) {
		return problem(HttpStatus.NOT_FOUND, e, locale);
	}

	@ExceptionHandler(OwnerRequiredException.class)
	ProblemDetail ownerRequired(OwnerRequiredException e, Locale locale) {
		return problem(HttpStatus.FORBIDDEN, e, locale);
	}

	@ExceptionHandler(HouseholdRuleViolation.class)
	ProblemDetail ruleViolated(HouseholdRuleViolation e, Locale locale) {
		return problem(HttpStatus.CONFLICT, e, locale);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e, Locale locale) {
		return problem(HttpStatus.BAD_REQUEST, e, locale);
	}

	/** E.g. two people accepting the same invitation at once: only one of them gets it. */
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
