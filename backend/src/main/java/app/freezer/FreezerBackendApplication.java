package app.freezer;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Freezer Manager — a modular monolith. Every direct sub-package is an application module that
 * implements one bounded context (see {@code docs/domain-model.md}).
 */
@SpringBootApplication
@EnableAsync // event reactions of other modules (@ApplicationModuleListener) run asynchronously
@Modulithic(systemName = "Freezer Manager", sharedModules = "sharedkernel")
public class FreezerBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FreezerBackendApplication.class, args);
	}

	/**
	 * Use cases take the current time from this clock, so tests can control it. It ticks in microseconds, the
	 * precision PostgreSQL stores, so a saved aggregate equals the one read back. "Today" (e.g. for expiry) is
	 * determined in the server's time zone.
	 */
	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemDefaultZone(), Duration.of(1, ChronoUnit.MICROS));
	}

}
