# JPA Tripwire

Tools for inspecting JPA/Hibernate models and databases to detect potential performance issues. Once burned, twice shy.

## Overview

I built JPA Tripwire to help developers identify common database performance issues rooted in the leaking abstraction that is an O/R Mapper - automating some good practices, so you don't have to. The tool analyzes your entity classes and the actual database schema, and can intercept EntityManager operations to trap some of the situations which may slow down your queries:

- N+1 selects due to unfortunate fetch strategies (remember: hope is not a strategy, nor is general belief in magic).
- Missing indexes on constraint columns (which might happen with hand-crafted DB migrations)
- JPA Repository operations without reasonable index coverage

The functionality is split into two oddly named tools:

## Unselectinator: How to spot N+1 selects in integration tests

`unselectinator-hibernate` provides Spring configuration to capture lazy loads.

Import [`UnselectinatorSpringConfiguration`](unselectinator-hibernate/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/hibernate/spring/UnselectinatorSpringConfiguration.java) to wire the [`Unselectinator`](unselectinator-core/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/core/Unselectinator.java), Hibernate listeners, and repository observation. The configuration instruments Spring-managed `EntityManagerFactory` and `EntityManager` instances; tests do not need to inject a special observed factory.

Nothing activates automatically, so each test class opts in explicitly. Example:

```java
@SpringBootTest(classes = MyApp.class)
@Import(UnselectinatorSpringConfiguration.class)
class MyIntegrationTest {
    @Autowired
    private Unselectinator unselectinator;

    @Autowired
    private ThingRepository thingRepository;

    @Test
    @Transactional
    void testThingWorksWell() {
        var observation = unselectinator.observe(() -> {
            var aThing = thingRepository.findByFrobKind("xyz");
            return aThing.thingWorks();
        });
        assertTrue(observation.value());
        assertEquals(0, observation.report().getLazySelectCount(),
                "Expected that things could work without lazy loads");
    }
}
```

For a value-returning operation, `observe` returns an `ObservationResult` containing
both the operation's value and its lazy-select report.

You can also put the reference into a shared test base class:

```java
@SpringBootTest(classes = { MyApp.class, UnselectinatorSpringConfiguration.class })
abstract class AbstractMyTest { }
```

See [`UnselectinatorIntegrationTest`](jpa-tripwire-test-parent/src/test/java/io/github/jespersm/jpa/tripwire/test/UnselectinatorIntegrationTest.java) for a working example.

The tracker observes entity loads and SELECT statements during repository and `EntityManager` operations to report lazy loads that may indicate an N+1 pattern. Hibernate is currently the only supported JPA provider.

### Unselectinator: Running with always-on enforcement

If you think you're "generally ok" w.r.t. 1+N-selects, you can use the Unselectinator
for *all* your integration tests. Each EntityManager will track loadded entities and collections
and tally the lazy loads, and throw exceptions when it crosses your threshold.

Add `@EnableUnselectinatorAlwaysOn` to a Spring test or its shared configuration.
It imports the instrumentation above and checks each open Hibernate session without
requiring an `observe(...)` block:

```java
@SpringBootTest(classes = MyApp.class)
@EnableUnselectinatorAlwaysOn(
        ratio = 0.5,
        minimumGroupSize = 6,
        minimumLazyLoads = 2,
        includes = {B.class}, // optional: all entity types by default
        excludes = {}       // optional: exclusions take precedence
)
@Transactional
class MyIntegrationTest {
    @Test
    void detectsLazySelects() {
        var bs = entityManager.find(A.class, 1L).getBs();
        // Loading this collection establishes a group; no role/property configuration.
        bs.forEach(b -> b.getC().getName());
    }
}
```

The guard discovers mapped to-one and collection associations automatically. Each
association gets a separate counter for each source group. The denominator is the
number of distinct entity members, including members with null or already-fetched
associations. Only successful lazy operations that execute JDBC statements increment
the numerator. A shared target or one batch initialization counts as one operation,
not once per owner. With six members and the configuration above, the fourth lazy
operation exceeds `0.5` and throws `CollectionLoadGuard.ThresholdExceededException`
after the load completes. Discard the EntityManager after a violation. Annotation
defaults are ratio `0.5`, minimum group size `5`, and minimum lazy operations `3`.
Includes/excludes apply to the entity type that **owns the checked association**,
including subclasses, rather than the target type.

Groups come from initialized collections (lists, sets, bags, and map **values**),
entity query results, and collections populated by fetch joins. Nested fetched
collections establish their own member groups. Query results are grouped by entity
type; entity-valued tuple/array results are supported, scalar/DTO-only results are
ignored. Wrapped query results include entities already present in the persistence
context. Collection-valued associations, element collections as lazy targets, and
read-only owners are supported. Loaded-state snapshots normally avoid application
getters; snapshotless read-only entities require Hibernate's property accessors for
properties already considered initialized.

Streams through the wrapped EntityManager collect distinct members as they are
consumed. Enforcement waits for exhaustion or explicit `close()`, when the consumed
group's size is final. Use try-with-resources and the owning transaction/thread;
a short-circuited stream must be closed. Fully materializing a stream before lazy
access allows immediate enforcement on those later accesses.

The report and guard modes share mapping metadata, a session-local reverse
association index, and fetch provenance. Exceptions include candidate origins and
the source location that triggered the lazy access. Query groups use a source such
as `<query:com.example.B>` in `violation().collectionRole()`. Session `clear()` and
close release tracking state. For native Hibernate, register a `CollectionLoadGuard`
integrator with `new CollectionLoadGuard.Configuration(0.5, 6, 2)`; wrap EntityManagers
with `ObservedEntityManagerFactory` and register the report integrator with the same
tracker to capture exact query boundaries and known fetch endpoints.

[`AlwaysOnUnselectinatorDemoTest`](jpa-tripwire-test-parent/src/test/java/io/github/jespersm/jpa/tripwire/test/AlwaysOnUnselectinatorDemoTest.java)
is the shared H2 demo for **both Spring Boot 3.5 and 4**. It demonstrates repository
results, a single collection load, and avoiding the lazy selects with a fetch join.
Run it without Docker:

```sh
./mvnw -pl jpa-tripwire-test-sb35,jpa-tripwire-test-sb4 -am test \
  -Dtest=AlwaysOnUnselectinatorDemoTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Always-on MVP limitations

- Configurable memory bounds, eviction policies, and session-size safeguards are
  intentionally deferred. State is deduplicated and cleared with the session, but
  long-lived sessions can accumulate groups and provenance.
- Individual SQL classification is intentionally deferred. Unrelated SQL outside
  lazy initialization is ignored. JDBC work performed inside custom load callbacks
  can make an otherwise cache-only initialization count; the guard counts operations,
  rather than distinguishing every statement or counting each row loaded by a batch.
- Raw/unwrapped Hibernate queries have best-effort cohorts based on newly loaded
  entities per JDBC execution. Cached results, stream boundaries, and exact result
  membership require the wrapped JPA query API. Scrollable results, async/parallel
  streams, stored procedures, scalar IDs, and entities hidden inside DTOs are not covered.
- Map keys and associations nested inside embeddables are not discovered as separate
  member/association groups. Enhanced entities whose lazy to-one value is represented
  only by an unfetched attribute (with no identifiable proxy) remain outside coverage;
  ordinary proxies and identifiable enhanced entity proxies are supported.
- A shared target can have several candidate owners/origins. Its SQL-producing load
  contributes once to every referencing group; the guard cannot prove which owner
  dereferenced it. Mutation/reparenting of already-captured groups is not tracked.
- Spring composition preserves existing inspector/integrator **instances**. Supply
  those Hibernate settings as instances when combining them with this configuration.

## Indexinator: How to assert your database is properly indexed

`indexinator-core` contains the [`Indexinator`](indexinator-core/src/main/java/io/github/jespersm/jpa/tripwire/indexinator/core/Indexinator.java) which can inspect your entities and produce an [`InspectionReport`](indexinator-core/src/main/java/io/github/jespersm/jpa/tripwire/indexinator/core/model/InspectionReport.java), and your tests can assert that there are no findings:

```java
try (Connection connection = dataSource.getConnection()) {
    InspectionReport report = Indexinator.builder()
            .withEntities(ThingLibrary.class, Thing.class, ThingAttribute.class)
            .withRepositories(ThingLibraryRepository.class, ThingRepository.class)
            .build()
            .inspect(connection);
    assertFalse(report.hasIssues(), "Expected no index findings for things in libraries.");
}
```

Pass the application's `EntityManagerFactory` to resolve table and column names through the JPA provider's mapping metadata. This takes naming strategies (such as Spring Boot's default `CamelCaseToUnderscoresNamingStrategy`) and `JOINED`/`SINGLE_TABLE` inheritance into account, which reading the annotations alone does not:

```java
try (Connection connection = dataSource.getConnection()) {
    InspectionReport report = Indexinator.builder()
            .withScannedEntities("com.myapp.entity")
            .withScannedRepositories("com.myapp.repository")
            .withEntityManagerFactory(entityManagerFactory)
            .build()
            .inspect(connection);
    assertFalse(report.hasIssues(), "Expected no index findings for mapped entities.");
}
```

See [`IndexinatorIntegrationTest`](jpa-tripwire-test-parent/src/test/java/io/github/jespersm/jpa/tripwire/test/IndexinatorIntegrationTest.java) for a working example.

### Detection types

| Issue Type | Severity | Description |
|------------|----------|-------------|
| Missing FK Index | HIGH/MEDIUM | Foreign key columns without indexes (JPA `@ManyToOne`, owning `@OneToOne`) |
| Missing Unique Index | MEDIUM | `@Column(unique = true)` columns without indexes |
| Missing Declared Index | MEDIUM | `@Table(indexes=...)` declared but not present in schema |
| Missing Query Index | MEDIUM | Columns used in Spring Data derived queries without indexes |
| Potential Composite Index | LOW | Opportunity for composite indexes based on access patterns |

### Hibernate Support

The optional `indexinator-hibernate` module provides a ServiceLoader-based
`RequirementMappingResolverProvider`. When an `EntityManagerFactory` is supplied,
it uses Hibernate mapping metadata to resolve physical table and column names,
including naming strategies and inheritance mappings.

## What it's not

This tool grew out of specific needs for better guardrails, so (more) developers could work on functionality, without the fear of introducing performance problems which would never show up in integrations tests, on nearly empty tables with local database. This tool does this, and it may work for you as well.

* However: It was built to fit my requirements, **not** yours - sorry!
  * Please open a bug if you feel there's something missing, or have an idea for a concrete improvement.

* I know it's **not** perfect - there are uncovered features, since Hibernate can do _so much_.

* The tools are **not** intended for use in your production code path due to the overhead, which I haven't measured for performance and/or memory overhead. Just run it with your integration tests.

* It's __not__ all you need to make application which runs efficiently and fast. Rather, remember to:
  * Set performance targets
  * Measure and observe in relevant environments
  * Plan and execute performance tests and load/stress tests - bottlenecks are never where you expect

* Also, I'm not a Hibernate expert by any means, and this tool is not a one-stop-shop of all you need to know. Please consult the great resources on JPA and Hibernate, such as:
  * [Vlad Mihalcea's Hibernate Tutorials](https://vladmihalcea.com/tutorials/hibernate/) contains a *bunch* of valuable insights, and his tool, the [Hypersistence Optimizer](https://vladmihalcea.com/hypersistence-optimizer/) might be an option for you
  * [Thorben Janssen's tutuorials on Hibernate and JPA](https://thorben-janssen.com/tutorials/) are full of sound advice
  * Baeldung has the extensive [Learn JPA & Hibernate Series](https://www.baeldung.com/learn-jpa-hibernate)
 
## Future directions

I tried to limit the framework lock-in as much as possible, and allow room for implementing variants for other EntityManager than Hibernate, but it hasn't been worked on.
Also, how about some more model vs. schema checks, e.g. for nullness and type details? Or other lints.

Please file an enhancement request describing the motivation, and we'll see. PRs are also very much welcomed! 
