# JPA Tripwire

Tools for inspecting JPA/Hibernate models and databases to detect potential performance issues. Once burned, twice shy.

## Overview

I built the JPA Tripwire to help developers identify common database performance issues rooted in the leaking abstraction that is an O/R Mapper.
 The tool analyzes your entity classes and the actual database schema to find discrepancies that can lead to slow queries and performance degradation:

- N+1 selects due to missing fetch graphs or general belief in magic
- Missing indexes on foreign key columns
- Missing indexes on unique constraint columns
- Tables without primary keys
- Missing indexes on frequently queried columns
- Suboptimal composite indexes

## Unselectinator: How to spot N+1 selects in integration test

`unselectinator-hibernate` contains Spring configuration beans to enable capturing lazy loads.

The main entry point is [`UnselectinatorSpringConfiguration`](unselectinator-hibernate/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/hibernate/spring/UnselectinatorSpringConfiguration.java), and with that, you can wrap observations in an [`Unselectinator`](unselectinator-core/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/core/Unselectinator.java), which registers the required instrumentation (using  [`EntityLoadTracker`](unselectinator-core/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/core/EntityLoadTracker.java), [`ObservedEntityManagerFactory`](unselectinator-core/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/core/ObservedEntityManagerFactory.java) and [`RepositoryFetchObservationAspect`](unselectinator-hibernate/src/main/java/io/github/jespersm/jpa/tripwire/unselectinator/hibernate/spring/RepositoryFetchObservationAspect.java)). 

Nothing activates automatically, so each test class opts in explicitly. Example:

```java
@SpringBootTest(classes = MyApp.class)
@Import(UnselectinatorSpringConfiguration.class)
class MyIntegrationTest {
    ...

    @Test
    @Transactional
    void testThingWorksWell() {
        var report = unselectinator.observe(() -> {
                var aThing = thingRepository.findByFrobKind("xyz");
                assertTrue(aThing.thingWorks());
        });
        assertEquals(0, report.getLazySelectCount(), "Expected that things could work without lazy loads");
    }
    ...
}
```

You can also put the reference into a shared test base class:

```java
@SpringBootTest(classes = { MyApp.class, UnselectinatorSpringConfiguration.class })
abstract class AbstractMyTest { }
```

See [`UnselectinatorIntegrationTest`](jpa-tripwire-test-parent/src/test/java/io/github/jespersm/jpa/tripwire/test/UnselectinatorIntegrationTest.java) for a working example.

The library provides a tracker-based interceptor that detects N+1 select patterns at runtime by monitoring Entity Manager loads and Spring Data repository method executions and the resulting SQL statements.
Wraps repository methods and tracks entity loads to identify when multiple queries are issued for related entities, indicating potential N+1 issues. Currently only supports Hibernate as a JPA provider.

### Always-on enforcement

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

Hibernate support is optional, in that a ServiceLoader-based provider (`RequirementMappingResolverProvider`) that maps JPA entity/property requirements to actual table/column names using Hibernate's metamodel API, enabling more accurate schema validation than using the basic introspection based on stock JPA table and schema mappings.

## Publishing releases

The `release` Maven profile generates sources, Javadoc, and PGP signatures before
publishing to Maven Central. The release workflow imports the signing key using
`actions/setup-java`. Configure these secrets for the `central-releases` environment:

- `CENTRAL_TOKEN_USER` and `CENTRAL_TOKEN_PASS`: Central Portal token credentials.
- `GPG_PRIVATE_KEY`: the ASCII-armored private signing key.
- `GPG_PASSPHRASE`: the signing key's passphrase.

Publish the corresponding public key to a keyserver supported by Central, as
described in [Central's PGP guide](https://central.sonatype.org/publish/requirements/gpg/).
The snapshot workflow skips signing and uses only the Central token credentials.

To retry a failed Central deployment, open **Actions → Release → Run workflow**,
select `main`, and enter the existing release tag (for example, `v1.0.0`). This
uses the current workflow from `main` to publish the tagged source without
creating another GitHub release. The tag must contain the signing configuration,
and its Maven version must match the tag without the `v` prefix. Only retry a
version that has not already been published successfully to Central.
