package io.github.jespersm.jpa.tripwire.test.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories({
        "io.github.jespersm.jpa.tripwire.test.library.repository",
        "io.github.jespersm.jpa.tripwire.test.library.naming"
})
public class LibraryTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryTestApplication.class, args);
    }
}
