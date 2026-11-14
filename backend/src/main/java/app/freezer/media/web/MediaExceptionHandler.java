package app.freezer.media.web;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.freezer.media.application.InvalidImageException;
import app.freezer.media.application.PhotoNotFoundException;
import app.freezer.sharedkernel.Translatable;

/**
 * Translates the media module's exceptions into problem details in the user's language.
 */
@RestControllerAdvice(basePackageClasses = MediaExceptionHandler.class)
class MediaExceptionHandler {

	private final MessageSource messages;

	MediaExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(InvalidImageException.class)
	ProblemDetail invalidImage(InvalidImageException e, Locale locale) {
		return problem(HttpStatus.BAD_REQUEST, e, locale);
	}

	@ExceptionHandler(PhotoNotFoundException.class)
	ProblemDetail notFound(PhotoNotFoundException e, Locale locale) {
		return problem(HttpStatus.NOT_FOUND, e, locale);
	}

	/** Photos are only ever added, never changed, so this is a safety net like in the other modules. */
	@ExceptionHandler({ OptimisticLockingFailureException.class, DuplicateKeyException.class })
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
