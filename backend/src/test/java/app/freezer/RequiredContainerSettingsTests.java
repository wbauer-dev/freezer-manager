package app.freezer;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * The container image refuses to start without explicit settings (profile {@code container}).
 */
class RequiredContainerSettingsTests {

	/** As in application-container.yml: every value refers to an environment variable. */
	static final Map<String, Object> CONTAINER_PROFILE = Map.of(
			"spring.datasource.url", "${SPRING_DATASOURCE_URL}",
			"spring.datasource.username", "${SPRING_DATASOURCE_USERNAME}",
			"spring.datasource.password", "${SPRING_DATASOURCE_PASSWORD}",
			"spring.security.oauth2.resourceserver.jwt.issuer-uri",
			"${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI}",
			"freezer.labels.scan-url", "${FREEZER_LABELS_SCANURL}");

	static final Map<String, Object> DEPLOYMENT = Map.of(
			"SPRING_DATASOURCE_URL", "jdbc:postgresql://db:5432/freezer",
			"SPRING_DATASOURCE_USERNAME", "freezer",
			"SPRING_DATASOURCE_PASSWORD", "secret",
			"SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI", "https://freezer.example/realms/freezer",
			"FREEZER_LABELS_SCANURL", "https://freezer.example/s/");

	@Test
	void startsWhenTheDeploymentSetsEverything() {
		assertThatNoException().isThrownBy(() -> RequiredContainerSettings.check(environment(DEPLOYMENT)));
	}

	@Test
	void refusesToStartWhenAVariableIsMissing() {
		var deployment = new HashMap<>(DEPLOYMENT);
		deployment.remove("SPRING_DATASOURCE_PASSWORD");

		assertThatIllegalStateException().isThrownBy(() -> RequiredContainerSettings.check(environment(deployment)))
				.withMessageContaining("spring.datasource.password")
				.withMessageContaining("SPRING_DATASOURCE_PASSWORD");
	}

	@Test
	void refusesToStartWhenAVariableIsBlank() {
		var deployment = new HashMap<>(DEPLOYMENT);
		deployment.put("FREEZER_LABELS_SCANURL", " ");

		assertThatIllegalStateException().isThrownBy(() -> RequiredContainerSettings.check(environment(deployment)))
				.withMessageContaining("freezer.labels.scan-url");
	}

	private static StandardEnvironment environment(Map<String, Object> deployment) {
		var environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("deployment", deployment));
		environment.getPropertySources().addLast(new MapPropertySource("application-container", CONTAINER_PROFILE));
		return environment;
	}

}
