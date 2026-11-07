# JPA Tripwire

[![codecov](https://codecov.io/gh/jespersm/jpa-tripwire/branch/main/graph/badge.svg)](https://codecov.io/gh/jespersm/jpa-tripwire)

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
