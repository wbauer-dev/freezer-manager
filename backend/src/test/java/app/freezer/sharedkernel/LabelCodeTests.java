package app.freezer.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class LabelCodeTests {

	@RepeatedTest(20)
	void generatedCodesAreValid() {
		var code = LabelCode.generate();

		assertThat(LabelCode.of(code.value())).isEqualTo(code);
	}

	@Test
	void isPrintedInTwoGroups() {
		assertThat(LabelCode.of("K7Q2M9XA").formatted()).isEqualTo("K7Q2-M9XA");
	}

	@Test
	void readsCodesAsPeopleTypeThem() {
		assertThat(LabelCode.parse(" k7q2-m9xa ")).isEqualTo(LabelCode.of("K7Q2M9XA"));
		assertThat(LabelCode.parse("ILO0-1234")).isEqualTo(LabelCode.of("11001234"));
	}

	@Test
	void rejectsWrongLengthAndCharacters() {
		assertThatIllegalArgumentException().isThrownBy(() -> LabelCode.parse("K7Q2"));
		assertThatIllegalArgumentException().isThrownBy(() -> LabelCode.of("K7Q2M9XU"));
	}

}
