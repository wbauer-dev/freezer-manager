package app.freezer;

import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The container image (profile {@code container}, see {@code application-container.yml}) only starts when the
 * deployment says where the database, the identity provider and the app are. Spring Boot binds some properties, e.g.
 * the datasource's, even when their placeholder could not be resolved — the application would then fail later and
 * obscurely, or worse, print a wrong link on every sticker. So all of them are checked before any bean is created (a
 * bean factory post-processor runs first).
 */
@Component
@Profile("container")
class RequiredContainerSettings implements BeanFactoryPostProcessor, EnvironmentAware {

	static final List<String> PROPERTIES = List.of("spring.datasource.url", "spring.datasource.username",
			"spring.datasource.password", "spring.security.oauth2.resourceserver.jwt.issuer-uri",
			"freezer.labels.scan-url");

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		check(environment);
	}

	/**
	 * @throws IllegalStateException if a property is missing, blank, or refers to an environment variable that is not
	 *         set
	 */
	static void check(Environment environment) {
		for (var property : PROPERTIES) {
			String value;
			try {
				// Unlike binding, this resolves placeholders strictly: an unset variable is an error.
				value = environment.getRequiredProperty(property);
			}
			catch (IllegalArgumentException | IllegalStateException e) {
				throw new IllegalStateException("The container needs " + property + ": " + e.getMessage(), e);
			}
			if (value.isBlank()) {
				throw new IllegalStateException("The container needs " + property + ", but it is blank");
			}
		}
	}

}
