# Multi-Version Spring Boot Tests (Shared Sources)

This setup keeps test code in one place and runs it against multiple Spring Boot lines.

## Modules

- `jpa-twca-test-parent/`
  - Holds all shared sources:
    - `src/main/java` (entities, repositories, test app)
    - `src/test/java` (integration tests)
    - `src/test/resources` (`application-test.properties`, `schema.sql`, `data.sql`)
  - Shared Java package is `io.github.jespersm.indexinator.test` (no version suffix).
  - Holds shared `dependencyManagement` and imports Spring Boot BOM via `spring.boot.test.version`.
- `jpa-twca-test-sb35/`
  - Source-less runner module for Spring Boot 3.5 + Java 17.
  - Uses `build-helper-maven-plugin` to compile/run shared sources from `jpa-twca-test-parent/src`.
- `jpa-twca-test-sb4/`
  - Source-less runner module for Spring Boot 4 + Java 21.
  - Also compiles/runs the exact same shared sources via `build-helper-maven-plugin`.

## Why This Pattern

- Single source of truth for both `main` and `test` code.
- Version modules stay minimal (mostly just POMs).
- Shared code is compiled in each runner module, so API/JDK compatibility is validated per Spring Boot line.

## Key POM Behavior

Each runner module (`jpa-twca-test-sb35`, `jpa-twca-test-sb4`) adds these shared directories:

- `../jpa-twca-test-parent/src/main/java` as main sources
- `../jpa-twca-test-parent/src/test/java` as test sources
- `../jpa-twca-test-parent/src/test/resources` as test resources

## Run Commands

```bash
# SB 3.5 line (Java 17+)
mvn -pl jpa-twca-test-sb35 -am test

# SB 4 line (Java 21+)
mvn -pl jpa-twca-test-sb4 -am test

# Compile-only check for SB4 (if Docker is unavailable)
mvn -pl jpa-twca-test-sb4 -am clean test -DskipTests
```

## Notes

- Tests use Testcontainers PostgreSQL, so Docker must be running for full test execution.
- `IndexinatorIntegrationTest` activates the `test` profile (`@ActiveProfiles("test")`) so SQL init files are always applied.



