package app.freezer;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Architecture rules inside each module. Package roles are declared with jMolecules annotations in
 * {@code package-info.java}: domain, application and infrastructure ring.
 */
class ArchitectureTests {

	static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("app.freezer");

	/** Dependencies only point inwards: infrastructure → application → domain. */
	@Test
	void layersFollowOnionArchitecture() {
		JMoleculesArchitectureRules.ensureOnionSimple().check(CLASSES);
	}

	/** E.g. aggregates reference other aggregates only by identifier. */
	@Test
	void dddBuildingBlocksAreUsedCorrectly() {
		JMoleculesDddRules.all().check(CLASSES);
	}

	/**
	 * The domain model is plain Java. Spring Data annotations are the only accepted exception: they are metadata.
	 * Mapping annotations keep the aggregate mapped as a whole, which is what Spring Data JDBC is designed for;
	 * {@code @DomainEvents} lets the repository publish an aggregate's events when it is saved.
	 */
	@Test
	void domainDependsOnSpringOnlyForSpringDataAnnotations() {
		noClasses().that().resideInAPackage("app.freezer..domain..")
				.should().dependOnClassesThat(resideInAPackage("org.springframework..")
						.and(not(resideInAnyPackage("org.springframework.data.annotation..",
								"org.springframework.data.relational.core.mapping..")))
						.and(not(equivalentTo(DomainEvents.class)))
						.and(not(equivalentTo(AfterDomainEventPublication.class))))
				.check(CLASSES);
	}

}
