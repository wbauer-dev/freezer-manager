package app.freezer;

import org.springframework.boot.SpringApplication;

public class TestFreezerBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(FreezerBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
