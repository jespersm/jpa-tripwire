# Unselectinator Hibernate Integration

`unselectinator-hibernate` wires Hibernate SQL and load events into `unselectinator-core`.

## Components

- `HibernateUnselectinatorStatementInspector`: counts `SELECT` SQL statements.
- `HibernateUnselectinatorIntegrator`: registers Hibernate event listeners.
- `HibernateUnselectinatorEventListener`: forwards `POST_LOAD` and `INIT_COLLECTION` events to `EntityLoadTracker`.
- `HibernateUnselectinator`: helper factory for statement inspector and integrator provider.

## Spring / Spring Boot Integration

`unselectinator-hibernate` also ships Spring wiring support in the `...hibernate.spring` package:

- `UnselectinatorSpringConfiguration`
- `RepositoryFetchObservationAspect`

The configuration decorates the Spring-managed `EntityManagerFactory` so EntityManagers
created for transactions, shared injection, and application-managed use record explicit
fetch endpoints. Service constructor injection and `@PersistenceContext` therefore
participate without requesting the `observedEntityManager` bean. Repository endpoints
remain the initiating endpoint when their calls nest EntityManager operations.

For usage and explicit per-test opt-in examples, see the top-level `README.md`.
