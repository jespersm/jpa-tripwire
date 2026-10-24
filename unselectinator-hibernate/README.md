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

## Experimental collection-triggered always-on guard

`CollectionLoadGuard` prototypes the `A.bs -> B.c` case from
[issue #39](https://github.com/jespersm/jpa-tripwire/issues/39). Register it once at
Hibernate bootstrap; application code uses ordinary EntityManagers without
`observe(...)` or EntityManager decoration:

```java
var guard = new CollectionLoadGuard(new CollectionLoadGuard.Configuration(
        A.class.getName() + ".bs", "c",
        0.5, // fail when lazy operations / distinct Bs strictly exceeds this ratio
        6,   // ignore collections with fewer than six distinct entity members
        2    // require at least two SQL-producing lazy operations
));

// Native Hibernate bootstrap. The guard and report integrator may be added in either order.
var bootstrap = new BootstrapServiceRegistryBuilder()
        .applyIntegrator(guard)
        .build();
var registry = new StandardServiceRegistryBuilder(bootstrap)
        // Add the application's JDBC and other Hibernate settings here.
        .build();

// Alternatively, JPA bootstrap (compose with existing integrators, if any):
properties.put("hibernate.integrator_provider", (IntegratorProvider) () -> List.of(guard));
```

Initializing the configured collection records its members' unresolved `c` proxies
using Hibernate's loaded state, without invoking application getters. Each collection
load has its own group, isolated within the real Hibernate session. Successful proxy
initialization that executes JDBC statements counts as **one** lazy operation, even
if batch fetching hydrates several targets. Already-loaded targets and cache-only
initializations do not count. Shared targets count once, rather than once per owner.

With six Bs and a limit of `0.5`, the first three qualifying lazy loads pass; the
fourth throws `CollectionLoadGuard.ThresholdExceededException`. Its `violation()`
includes the collection role, owner ID, association, member count, lazy-load count,
ratio limit, and original fetch provenance. The exception is raised after Hibernate completes the triggering
load. A failing test should discard its EntityManager as with other Hibernate errors.

The guard retains entity keys and group counters, not entity instances or event
histories. Session close releases its state; `clear()` discards pending groups. Weak
session keys provide a fallback for abandoned sessions. Existing `observe(...)`
reports remain independent of the guard.

## Shared association cache and fetch origins

The guard and report integration share one `HibernateUnselectinatorEventListener`
per SessionFactory. It caches to-one and collection mapping metadata once per entity mapping and
builds a reverse index from unresolved target keys to candidate owner associations.
Lookup during a lazy load visits those candidates rather than scanning all managed
entities or inspecting annotated fields. Loaded-state snapshots support field,
property, and XML mappings without invoking application getters. Read-only entities
without snapshots use Hibernate accessors for initialized properties. Collection report
edges come directly from Hibernate's initialized collection as well.

Each indexed relationship carries a `FetchOrigin`: a fetch invocation ID, its known
explicit `FetchEndpoint` and source location, and shared parent links through the
traversed associations. Repeated executions of the same query retain separate fetch
IDs. Interleaving other queries does not replace an owner's origin. Entities shared
by several collection groups retain all candidate origins.

`LazySelectEvent.attributions()` exposes every candidate owner, association, and
origin. The existing `owner()`, `relationName()`, and `initiatingEndpoint()` summary
fields are populated only where the candidates agree. Shared targets still produce
one load event. `countLazySelectsInitiatedBy()` counts that event once for each
matching endpoint signature, so counts across different signatures may overlap for
ambiguous events. Text reports and guard exceptions also include origins. Native
unwrapped fetches preserve captured source locations but leave unknown API endpoints
unset; they are not assigned the last unrelated explicit fetch.

As requested in [issue #35](https://github.com/jespersm/jpa-tripwire/issues/35),
`LazySelectEvent.triggerLocation()` records the application access that actually
triggered the lazy load, with class, method, source file, and line number. This is
separate from the original fetch endpoint and cached traversal origins. Guard
violations expose `triggerLocation()` too, and text reports and exceptions print it.
Generated Hibernate and Spring proxy frames are excluded. Repeated call sites share
one immutable `SourceLocation`, cached with `ClassValue` so application class loaders
can still be collected. Trigger locations are captured when the load is detected,
not stored as an association's creation site in the index.

Session close and `clear()` release session-specific index data; evicting an owner
removes its pending association entries. Completed targets release their reverse
edges. The index retains entity keys and immutable provenance, never managed entity
instances. Mapping metadata is shared across sessions and cleared with the factory.
When only report mode is installed, attribution remains inactive outside `observe(...)`.

## Always-on MVP

The Spring entry point is `@EnableUnselectinatorAlwaysOn`, with automatic to-one
and collection association discovery, ratio/minimum thresholds, and entity
includes/excludes. The three-argument `CollectionLoadGuard.Configuration` enables
this automatic mode for native bootstrapping; the five-argument form above retains
explicit collection-role/property selection.

Entity query results and fetch-joined collections establish groups too. Maps use
their entity values. Read-only mappings use initialized Hibernate property accessors
when no loaded snapshot exists. Identifiable enhanced entity proxies are indexed;
unfetched attributes without identifiable targets are not. Wrapped streams defer
enforcement until exhaustion or close.

See the root [Unselectinator documentation](../README.md#always-on-enforcement) for
the annotation example, shared Spring Boot 3.5/4 demo, counting semantics, and the
[deliberately deferred features and remaining limitations](../README.md#always-on-mvp-limitations).
In particular, tracking limits and individual SQL classification are not part of
this MVP; raw query boundaries and mutable cohorts have limited coverage.

Run the focused H2 integration tests without Docker:

```sh
./mvnw -pl unselectinator-hibernate -am test \
  -Dtest=CollectionLoadGuardTest,HibernateUnselectinatorLazyLoadTest,EnhancedAlwaysOnTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
