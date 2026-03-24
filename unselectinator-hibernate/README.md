# Unselectinator Hibernate Integration

`unselectinator-hibernate` wires Hibernate SQL and load events into `unselectinator-core`.

## Components

- `HibernateUnselectinatorStatementInspector`: counts `SELECT` SQL statements.
- `HibernateUnselectinatorIntegrator`: registers Hibernate event listeners.
- `HibernateUnselectinatorEventListener`: forwards `POST_LOAD` and `INIT_COLLECTION` events to `EntityLoadTracker`.
- `HibernateUnselectinator`: helper factory for statement inspector and integrator provider.

## Spring Integration in This Repository

The shared test fixture (`jpa-twca-test-parent`) demonstrates one-stop setup in:

- `jpa-twca-test-parent/src/main/java/io/github/jespersm/jpa-twca/testunselectinator/UnselectinatorDemoConfiguration.java`
- `jpa-twca-test-parent/src/main/java/io/github/jespersm/jpa-twca/testunselectinator/RepositoryFetchObservationAspect.java`

See integration tests in:

- `jpa-twca-test-parent/src/test/java/io/github/jespersm/jpa-twca/testIndexinatorIntegrationTest.java`

