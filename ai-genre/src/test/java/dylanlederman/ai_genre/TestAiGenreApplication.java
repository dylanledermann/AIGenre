package dylanlederman.ai_genre;

import org.springframework.boot.SpringApplication;

public class TestAiGenreApplication {

	public static void main(String[] args) {
		SpringApplication.from(AiGenreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
