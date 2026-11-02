package io.github.jespersm.jpa.tripwire.test.students;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("io.github.jespersm.jpa.tripwire.test.students.repository")
public class StudentsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentsTestApplication.class, args);
    }
}
