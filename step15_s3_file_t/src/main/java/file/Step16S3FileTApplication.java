package file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing //이걸 넣어줘야 데이터 들어감
@SpringBootApplication
public class Step16S3FileTApplication {

	public static void main(String[] args) {
		SpringApplication.run(Step16S3FileTApplication.class, args);
	}

}
