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
