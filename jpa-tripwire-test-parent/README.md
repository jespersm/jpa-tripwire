# Multi-Version Spring Boot Tests (Shared Sources)

This setup keeps test code in one place and runs it against multiple Spring Boot lines.

## Modules

- `jpa-tripwire-test-parent/`
  - Holds all shared sources:
    - `src/main/java` (entities, repositories, test app)
    - `src/test/java` (integration tests)
    - `src/test/resources` (test profiles and SQL fixtures)
  - Shared fixtures include the `io.github.jespersm.jpa.tripwire.test` and `io.github.jespersm.jpa.tripwire.librarytest` packages.
  - Holds shared `dependencyManagement` and imports Spring Boot BOM via `spring.boot.test.version`.
- `jpa-tripwire-test-sb35/`
  - Source-less runner module for Spring Boot 3.5, targeting Java 17.
  - Uses `build-helper-maven-plugin` to compile/run shared sources from `jpa-tripwire-test-parent/src`.
- `jpa-tripwire-test-sb4/`
  - Source-less runner module for Spring Boot 4, targeting Java 17.
  - Also compiles/runs the exact same shared sources via `build-helper-maven-plugin`.

## Why This Pattern

- Single source of truth for both `main` and `test` code.
- Version modules stay minimal (mostly just POMs).
- Shared code is compiled in each runner module, so API/JDK compatibility is validated per Spring Boot line.

## Key POM Behavior

Each runner module (`jpa-tripwire-test-sb35`, `jpa-tripwire-test-sb4`) adds these shared directories:

- `../jpa-tripwire-test-parent/src/main/java` as main sources
- `../jpa-tripwire-test-parent/src/test/java` as test sources
- `../jpa-tripwire-test-parent/src/test/resources` as test resources

## Run Commands

```bash
# SB 3.5 line (Java 17+)
./mvnw -pl jpa-tripwire-test-sb35 -am test

# SB 4 line (Java 17+)
./mvnw -pl jpa-tripwire-test-sb4 -am test

# Compile-only check for SB4 (if Docker is unavailable)
./mvnw -pl jpa-tripwire-test-sb4 -am clean test -DskipTests
```

## Notes

- Tests use Testcontainers PostgreSQL, so Docker must be running for full test execution.
- `AbstractTripwireTest` and `AbstractLibraryTripwireTest` activate the `test` and `library-test` profiles, respectively, to apply the matching SQL fixtures.
