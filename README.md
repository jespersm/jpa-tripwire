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
