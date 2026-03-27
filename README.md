# JPA Tripwire

Tools for inspecting JPA/Hibernate models and databases to detect potential performance issues. Once burned twice shy.

## Overview

I built the JPA Tripwire to help developers identify common database performance issues rooted in the leaking abstraction that is an O/R Mapper.
 The tool analyzes your entity classes and the actual database schema to find discrepancies that can lead to slow queries and performance degradation:

- N+1 selects due to missing fetch graphs or general belief in magic
- Missing indexes on foreign key columns
- Missing indexes on unique constraint columns
- Tables without primary keys
- Missing indexes on frequently queried columns
- Suboptimal composite indexes

## Project Structure

This is a multi-module Maven project:

```
jpa-tripwire/
├── indexinator-core/          # Core inspection library
│   └── Reusable library for JPA/database inspection
│
├── indexinator-hibernate/     # Hibernate schema informationprovider extension
│   └── ServiceLoader-based mapping resolver for requirement-to-schema mapping
│
├── unselectinator-core/       # Core interceptor library for detecting N+1 selects
│   └── Reusable library for repository method interception and query analysis
│
├── unselectinator-hibernate/     # Hibernate event provider extension
│   └── ServiceLoader-based interceptor provider for detecting N+1 selects at runtime
│
├── jpa-tripwire-test-parent/   # Shared Spring Boot test fixture sources
│   └── Main/test sources reused by versioned test runners
│
├── jpa-tripwire-test-sb35/     # Source-less Spring Boot 3.5 runner
│   └── Java 17 runner compiling shared test-parent sources
│
└── jpa-tripwire-test-sb4/      # Source-less Spring Boot 4 runner
    └── Java 21 runner compiling shared test-parent sources
```

## Features

### Indexinator

The core library provides:

- **Entity Analysis**: Extracts metadata from JPA entities using reflection
- **Schema Inspection**: Uses JDBC metadata to inspect actual database schema
- **Issue Detection**: Compares entity metadata with database schema to find issues
- **Detailed Reporting**: Generates comprehensive reports with severity levels and recommendations

#### Missing Index Detection

| Issue Type | Severity | Description |
|------------|----------|-------------|
| Missing FK Index | HIGH/MEDIUM | Foreign key columns without indexes (JPA `@ManyToOne`, owning `@OneToOne`) |
| Missing Unique Index | MEDIUM | `@Column(unique = true)` columns without indexes |
| Missing Declared Index | MEDIUM | `@Table(indexes=...)` declared but not present in schema |
| Missing Query Index | MEDIUM | Columns used in Spring Data derived queries without indexes |
| Potential Composite Index | LOW | Opportunity for composite indexes based on access patterns |

#### Indexinator Hibernate Extension 

A ServiceLoader-based provider (`RequirementMappingResolverProvider`) that maps JPA entity/property requirements to actual table/column names using Hibernate's metamodel API, enabling accurate schema validation.

### Unselectinator

The core library provides a tracker-based interceptor that detects N+1 select patterns at runtime by monitoring Entity Manager loads and Spring Data repository method executions and the resulting SQL statements.
Wraps repository methods and tracks entity loads to identify when multiple queries are issued for related entities, indicating potential N+1 issues.

#### Unselectinator Hibernate Extension

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (required for Testcontainers in integration tests)

Building and running the tests is just plain Maven.

## Development Notes

### Publishing

(just a note to self, I do this so rarely I forget the exact steps)

Publishing is configured in `pom.xml` through a `release` profile that enables `org.sonatype.central:central-publishing-maven-plugin`.

### One-time Maven credentials setup

Add Central Portal credentials to `~/.m2/settings.xml` using the same server id as the POM (`central`):

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_TOKEN_USER}</username>
      <password>${env.CENTRAL_TOKEN_PASS}</password>
    </server>
  </servers>
</settings>
```

### Publish a SNAPSHOT

1. Keep the project version as `*-SNAPSHOT` (for example `1.0.0-SNAPSHOT`).
2. Ensure SNAPSHOTs are enabled for your namespace in the Central Portal.
3. Run deploy with the `release` profile:

```zsh
cd jpa_tripwire
./mvnw -Prelease -DskipTests -DskipITs deploy
```

Notes from Central Portal SNAPSHOT docs:
- SNAPSHOT publishing skips the normal Central validation checks.
- SNAPSHOTs are not permanent and can be cleaned up by Central over time.
- If you see `Skipping Central Snapshot Publishing ... at user's request`, remove `-DskipPublishing=true` from your command.

### Publish a release

1. Set a non-snapshot version (for example `1.0.0`).
2. Tag and push the release commit.
3. Run:

```zsh
cd jpa_tripwire
./mvnw -Prelease -DskipTests -DskipITs deploy
```

Test modules (`jpa-tripwire-test-*`) are excluded from the Central bundle via `central-publishing-maven-plugin` `excludeArtifacts` in `pom.xml`.

