## Development Notes

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (required for Testcontainers in integration tests)

Building and running the tests uses the Maven wrapper (`./mvnw`).

### Tests

The Spring Boot 3.5 and 4 runners execute the same integration tests from
`jpa-tripwire-test-parent`. Run each supported line with:

```bash
./mvnw -pl jpa-tripwire-test-sb35 -am test
./mvnw -pl jpa-tripwire-test-sb4 -am test
```

These tests use Testcontainers PostgreSQL and require Docker. Test profiles set
the PostgreSQL dialect explicitly and suppress routine Spring/Hibernate output,
the Spring banner, and Tripwire's expected lazy-load warnings. Those warnings
are the behavior under test; the tests assert on their reports instead. Raise
the relevant logger levels locally when diagnosing a failure.

### Project Structure

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

### Publishing

Publishing is automated with GitHub Actions and Maven Central's token-based authentication.
No GPG setup is required for this repository's Central flow.

#### Workflows

- [`.github/workflows/maven.yml`](.github/workflows/maven.yml)
  - Runs CI on pull requests to `main` and on pushes to `main`.
  - Uses no Central credentials.
- [`.github/workflows/publish-snapshot.yml`](.github/workflows/publish-snapshot.yml)
  - Runs only on pushes to `main`.
  - Publishes to Central only when the Maven version ends with `-SNAPSHOT`.
  - Requires the `main` ref to be protected (`github.ref_protected == true`).
- [`.github/workflows/release.yml`](.github/workflows/release.yml)
  - Runs `release-please` on pushes to `main`.
  - Opens/updates a release PR based on Conventional Commit history.
  - When that release PR is merged and an actual release is created by `release-please`, the workflow publishes the release artifacts to Central.

Release-please is configured via [`release-please-config.json`](release-please-config.json) and [`.release-please-manifest.json`](.release-please-manifest.json).

This split is deliberate: untrusted pull requests never receive Central credentials, and release publishing is not triggered from PR events, `pull_request_target`, or arbitrary tags.

#### One-time GitHub setup

Create two GitHub Environments in the repository settings:

1. `central-snapshots`
2. `central-releases`

Add these secrets to both environments:

- `CENTRAL_TOKEN_USER`
- `CENTRAL_TOKEN_PASS`

Recommended protections:

- Protect the `main` branch.
- Require the CI workflow to pass before merge.
- Require Code Owner review before merging changes to publishing workflows and release-related POM files.
- Add required reviewers on `central-releases`.
- Optionally add required reviewers on `central-snapshots` too if you want manual approval even for snapshots.

The repository includes [`.github/CODEOWNERS`](.github/CODEOWNERS) so you can turn on GitHub's "Require review from Code Owners" branch protection and ensure release-critical changes are explicitly reviewed.

#### Semantic versioning rules

Release automation follows Conventional Commit semantics:

- `fix:` -> patch release
- `feat:` -> minor release
- `feat!:` or a `BREAKING CHANGE:` footer -> major release
- `docs:`, `test:`, `chore:` and similar commits are ignored for version bumps unless marked as breaking

Examples:

```text
fix: handle composite index edge case
feat: detect missing indexes on join tables
feat!: rename inspection API to simplify usage
```

#### How releases now work

1. Merge normal commits into `main`.
2. `release-please` keeps a release PR up to date with the next semantic version and changelog.
3. Review and merge that release PR.
4. The trusted release workflow publishes the release to Central using the Central token.

#### How snapshots now work

1. Keep the project version on a `*-SNAPSHOT` version in `main`.
2. Merge to `main`.
3. The snapshot workflow publishes automatically to Central from the protected `main` branch.

Test modules (`jpa-tripwire-test-parent`, `jpa-tripwire-test-sb35`, `jpa-tripwire-test-sb4`) are also marked with `maven.deploy.skip=true` and excluded from the Central bundle so test fixtures cannot be published accidentally.

#### Optional local publish command

To verify the Central profile locally, run `./mvnw -B -Prelease -DskipTests deploy`.
The generated Maven settings must use the `central` server id from the root POM.
