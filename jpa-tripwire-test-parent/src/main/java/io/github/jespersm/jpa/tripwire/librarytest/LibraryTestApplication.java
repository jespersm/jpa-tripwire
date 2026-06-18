package io.github.jespersm.jpa.tripwire.librarytest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("io.github.jespersm.jpa.tripwire.librarytest.repository")
public class LibraryTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryTestApplication.class, args);
    }
}
