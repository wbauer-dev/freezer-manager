package app.freezer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Hands events whose reaction failed (e.g. a database hiccup, or a change that kept colliding) to their listeners
 * again, every few minutes — not only after a restart. Only failed publications at least a few minutes old: events
 * still being handled are left alone, and a failing reaction is not retried in a tight loop. Reactions tolerate events
 * they have already handled, so a second delivery is harmless.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class FailedEventResubmission {

	static final Duration MIN_AGE = Duration.ofMinutes(5);

	private final IncompleteEventPublications publications;

	FailedEventResubmission(IncompleteEventPublications publications) {
		this.publications = publications;
	}

	@Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
	void resubmitFailedPublications() {
		publications.resubmitIncompletePublications(ResubmissionOptions.defaults().withMinAge(MIN_AGE));
	}

}
