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

	/**
	 * Writes C4 and module diagrams to {@code target/spring-modulith-docs}.
	 * <p>
	 * Deliberately not {@code writeDocumentation()}: it also writes the module metadata to
	 * {@code target/classes/META-INF/spring-modulith/application-modules.json}. Spring Modulith prefers that file to
	 * scanning the code when the application starts, so after a non-clean build the application (and every later test)
	 * would only know the modules of an earlier build — and skip the migrations of a new module.
	 */
	@Test
	void writesDocumentation() {
		new Documenter(MODULES)
				.writeModulesAsPlantUml()
				.writeIndividualModulesAsPlantUml()
				.writeModuleCanvases()
				.writeAggregatingDocument();

		assertThat(Path.of("target", "spring-modulith-docs", "components.puml")).isNotEmptyFile();
	}

}
