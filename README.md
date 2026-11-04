# Group1Fork: world population reports

[![Build and test](https://github.com/KyleGit/Group1Fork/actions/workflows/main.yml/badge.svg?branch=develop)](https://github.com/KyleGit/Group1Fork/actions/workflows/main.yml)
[![License](https://img.shields.io/github/license/KyleGit/Group1Fork.svg?style=flat-square)](LICENSE)
[![Releases](https://img.shields.io/github/v/release/KyleGit/Group1Fork?include_prereleases&style=flat-square)](https://github.com/KyleGit/Group1Fork/releases)
[![Coverage](https://codecov.io/gh/KyleGit/Group1Fork/branch/develop/graph/badge.svg)](https://codecov.io/gh/KyleGit/Group1Fork)

A Java application that queries the supplied MySQL `world` database and prints country, city and capital-city reports in descending population order. See the [Code Review 3 guide](docs/code-review-3.md) for the test strategy, assessment evidence and remaining requirements.

## Requirements

- Java 17 and Maven.
- Docker with Docker Compose v2 for the seeded database, integration tests and container run.

Run commands from the repository root. The database image imports `db/world.sql` and exposes MySQL on `localhost:33060`.

## Build and unit tests

```shell
mvn clean verify
```

This runs the database-independent unit tests, generates JaCoCo coverage and packages the application with its dependencies as `target/semApp.jar`. Open `target/site/jacoco/index.html` for coverage and `target/surefire-reports/` for test results.

## Integration tests

```shell
docker compose up --build --detach --wait db
mvn clean verify -Pintegration
```

The database health check waits for the seeded country records, rather than only an open port. The `integration` profile runs the unit tests and then `ReportsIT` against the real seeded MySQL database; an unavailable database fails the integration run. Results are in `target/failsafe-reports/`, and JaCoCo includes both test suites.

The integration-test address defaults to `localhost:33060`. Override it when using another database containing the same seed data:

```shell
mvn clean verify -Pintegration -Ddb.address=localhost:33060
```

## Run reports

After packaging, run the application in Docker:

```shell
docker compose up --build --abort-on-container-exit --exit-code-from app
```

Or keep the database running and execute the JAR locally:

```shell
java -jar target/semApp.jar localhost:33060 1000
```

The optional arguments are database `host:port` and retry delay in milliseconds. Without an address argument, the application uses `DB_ADDRESS`, falling back to `db:3306`; the default retry delay is `1000`. `DB_USER` and `DB_PASSWORD` override the demonstration credentials `root` and `example`. The supplied Compose database uses those demonstration credentials.

The entry point prints example reports using predefined filters and limits. The report methods accept place filters and a user-supplied positive `N`; an interactive report menu is not implemented.

Clean up the local containers and their database volumes when finished:

```shell
docker compose down --volumes
```

## Continuous integration

[GitHub Actions](https://github.com/KyleGit/Group1Fork/actions/workflows/main.yml) runs on pushes and pull requests, with separate unit-test and integration-test jobs. It starts the seeded database, verifies the report queries and runs the packaged application in Docker.

- `unit-test-results` contains unit-test results and JaCoCo coverage.
- `integration-test-results` contains unit/integration results, combined coverage, `semApp.jar` and the application console report when generated.

Codecov upload is optional: configure the repository secret `CODECOV_TOKEN` to publish coverage there. Tests, local coverage and saved Actions artifacts do not depend on Codecov. The coverage badge needs a successful upload before it can display a result.

## Feature status

The country, city and capital-city families provide 22 report query variants, including place filters and top-`N` limits. The three population breakdown reports, six population lookups and language-speaker report remain unimplemented. The team must judge progress against its estimates; counting report methods alone does not establish the assessment's 50% completion target.
