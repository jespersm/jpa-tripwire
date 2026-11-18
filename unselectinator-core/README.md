# Unselectinator Core

`unselectinator-core` provides provider-agnostic lazy-select observation primitives.

## Main Concepts

- `EntityLoadTracker`: thread-local tracker for explicit fetch scopes and load/select events.
- `Unselectinator`: tiny facade to run code and capture a `LazySelectReport`.
- `FetchEndpoint`: caller-visible initiating operation (`REPOSITORY_METHOD` or `ENTITY_MANAGER`).
- `FetchOrigin`: an individual fetch invocation and its immutable parent chain through associations.
- `AssociationAttribution`: a candidate owner association with its original fetch provenance.
- `ObservedEntityManagerFactory`: wraps `EntityManager`/`Query` calls and marks explicit `EntityManager` fetch boundaries.

## Typical Usage

```java
ObservationResult<List<String>> result = unselectinator.observe(() -> {
    SchoolClass schoolClass = schoolClassRepository.findByCourseCode("CS101").orElseThrow();
    return schoolClass.getStudents().stream()
            .map(student -> student.getClasses().size())
            .map(String::valueOf)
            .toList();
});

LazySelectReport report = result.report();
```

`EntityLoadTracker` prefers repository endpoints when a repository call nests `EntityManager` operations.

Hibernate-backed reports populate `LazySelectEvent.attributions()` from a session-local
association index. These candidates retain the fetch origin across interleaved queries
and collection traversal. Multiple possible owners or origins remain explicit; the
legacy event summary fields are populated only where candidates agree. Fetch IDs
distinguish executions even when their endpoint and call site are identical.

`LazySelectEvent.triggerLocation()` separately identifies the application caller
that triggered a lazy load. It does not replace the earlier fetch origin. Report
text includes both; repeated caller locations share a class-loader-safe cached value.

