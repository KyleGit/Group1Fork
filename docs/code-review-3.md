# Code Review 3 guide

## Assessment scope

The [coursework assessment brief](https://github.com/Kevin-Sim/SET08103/blob/master/assessment/README.md#code-review-3) requires suitable unit tests, suitable integration tests and tests running on GitHub Actions. These form the 12-mark checklist. The remaining eight marks assess contribution metrics, code quality and comments, branch usage, working continuous integration, Kanban use, unit-test quality and coverage, and implemented project requirements. The stated aim is at least 50% of the work complete according to the team's estimates.

The implementation follows [Lab 6: Unit Testing](https://github.com/Kevin-Sim/SET08103/blob/master/labs/lab06/README.md) and [Lab 7: Integration Testing](https://github.com/Kevin-Sim/SET08103/blob/master/labs/lab07/README.md), adapted to the population-report coursework. [Lab 8: Deployment](https://github.com/Kevin-Sim/SET08103/blob/master/labs/lab08/README.md) introduces release automation; deployment is a Code Review 4 requirement. Its report publishing, REST service and web front-end sections are optional extras and are outside this CR3 change.

## Test strategy

| Suite | Purpose | Cases covered |
| --- | --- | --- |
| `UnitTests` | Check individual behaviors without a database | Null/blank filters; null, zero and negative top-`N` limits; missing connection; output headings and values; null/empty lists and null members; missing field display; mapping of result columns; bound SQL parameters; query failure and resource cleanup; connection settings and interruption; command-line validation. |
| `ReportsIT` | Verify report SQL and JDBC behavior against seeded MySQL | All 22 report query variants; expected records and column values; descending population order; continent/region/country/district filters; top-`N` limits after filtering; limits exceeding result size; countries without capitals; actual designated capitals; unknown and quoted filters. |

The unit suite uses controlled JDBC substitutes to check mapping, parameter binding, failures and resource closure. Those checks do not establish that SQL works: the integration suite executes the queries against `db/world.sql` and asserts known results. For example, the full country query must retain Antarctica despite its missing capital, and the top-capital query must exclude Mumbai, which is a larger non-capital city.

The integration suite expects the supplied, unchanged seed data. It fails when the database cannot be reached rather than skipping tests or passing with empty results. It does not modify the seed data.

### Input and failure behavior

- Report filters that are null or blank throw `IllegalArgumentException`.
- Top-`N` limits that are null, zero or negative throw `IllegalArgumentException`.
- Unknown, nonblank place names return empty result lists.
- Filters and limits are bound as prepared-statement parameters, including values containing quotes.
- Reports requested without a connection, failed database connections and failed queries throw `IllegalStateException` so failed reports cannot look like valid empty results.
- Printing a null, empty or null-only list produces an explicit no-data message. Null members in a populated list are skipped, and missing text fields print `-`.

## Reproduce the checks

Install Java 17, Maven and Docker Compose v2, then run from the repository root:

```shell
mvn clean verify
docker compose up --build --detach --wait db
mvn clean verify -Pintegration
docker compose up --build --abort-on-container-exit --exit-code-from app
docker compose down --volumes
```

`mvn clean verify` runs unit tests without MySQL. The `integration` profile adds Failsafe integration tests after packaging. Its `db.address` property defaults to `localhost:33060`; override it with `-Ddb.address=host:port`. The application uses `DB_USER` and `DB_PASSWORD`, defaulting to `root` and `example`, to connect to the `world` database.

For local Java debugging, leave the database running and execute:

```shell
java -jar target/semApp.jar localhost:33060 1000
```

The database address and retry delay are optional. With no address argument the application reads `DB_ADDRESS`, falling back to the Compose address `db:3306`. The retry delay defaults to `1000` milliseconds.

### Results to present

Local validation on 4 November 2026 used Java 17 and a fresh MySQL 8.0.46
database imported from `db/world.sql`: all 45 unit tests and 14 integration
tests passed, with no skipped tests. Combined JaCoCo line coverage was
95.5% (236 of 247 lines); branch coverage was 90.9% (60 of 66 branches).
The packaged JAR also ran successfully against that database, and the
workflow passed actionlint validation. Docker was unavailable locally.
For verification on GitHub, inspect the successful Actions run and artifacts
for the exact revision being reviewed using the links below.

- `target/surefire-reports/`: unit-test results.
- `target/failsafe-reports/`: integration-test results when the profile runs.
- `target/site/jacoco/index.html`: coverage report; the integration-profile run includes both suites.
- `target/semApp.jar`: self-contained application.

The [Actions workflow](../.github/workflows/main.yml) runs on pushes and pull requests. The unit-test job must succeed before the integration-test job starts. The latter builds and waits for seeded MySQL, runs both suites and executes the application container. It saves `target/application-report.txt` as report evidence.

Download `unit-test-results` and `integration-test-results` from the workflow run to inspect the generated results. The integration artifact also contains the JAR and console report when those steps complete. Artifacts are uploaded even after a failure, so inspect the job status and result files rather than treating artifact existence as proof of success.

JaCoCo coverage is generated and retained without an external account. Optional Codecov uploads use `CODECOV_TOKEN` and do not fail the build if upload fails. A Codecov badge alone is not proof that the current revision passed. Present the successful Actions run for the exact revision being reviewed and its test/coverage evidence; record measured counts and percentages from that run.

## Implemented requirements and remaining work

| Report family | Query variants implemented |
| --- | --- |
| Countries | All countries and top-`N` countries, each for world, continent and region: six variants. |
| Cities | All cities and top-`N` cities, each for world, continent, region, country and district: ten variants. |
| Capital cities | All capitals and top-`N` capitals, each for world, continent and region: six variants. |

These 22 query variants expose the required report columns and are covered by integration tests. The demonstration entry point prints predefined examples; report parameters are accepted by the methods, but an interactive input menu is not implemented.

The following requirements remain unimplemented:

- Population breakdown reports for each continent, region and country, including urban/non-urban totals and percentages: three reports.
- Population lookups for world, continent, region, country, district and city: six lookups.
- Ranked Chinese, English, Hindi, Spanish and Arabic speaker totals, including percentages of world population: one report.

The team must assess the 50% completion target using its backlog and estimates. A count of query variants does not measure all remaining effort.

## Team evidence still required

Code changes cannot establish the team's project-management or individual-contribution evidence. Before the review, the team should:

- Keep the Kanban board and sprint progress current, linked to actual issues and contributions.
- Demonstrate the branch workflow and successful CI run for the reviewed revision.
- Be ready to explain the tested behaviors, assertions, SQL and remaining requirements using a building version of the application.
- Agree individual contributions in the Moodle spreadsheet, with percentages totaling 100% for this review.
- Submit the master-branch repository link and contribution spreadsheet to Moodle as required by the assessment brief.

Review the current course guidance for submission details. Do not claim complete coursework delivery from these tests alone.
