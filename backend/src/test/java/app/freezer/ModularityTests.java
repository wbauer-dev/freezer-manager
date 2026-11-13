package app.freezer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the bounded contexts stay separated: modules only use each other's published API (the module's base
 * package) and there are no dependency cycles.
 */
class ModularityTests {

	static final ApplicationModules MODULES = ApplicationModules.of(FreezerBackendApplication.class);

	@Test
	void verifiesModularStructure() {
		MODULES.verify();
	}

	/** Writes C4 and module diagrams to {@code target/spring-modulith-docs}. */
	@Test
	void writesDocumentation() {
		new Documenter(MODULES).writeDocumentation();

		assertThat(Path.of("target", "spring-modulith-docs", "components.puml")).isNotEmptyFile();
	}

}
