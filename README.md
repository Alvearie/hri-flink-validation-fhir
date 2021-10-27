# HRI Flink Validation FHIR

The Alvearie Health Record Ingestion service: a common 'Deployment Ready Component' designed to serve as a “front door” for data for cloud-based solutions. See our [documentation](https://alvearie.io/HRI) for more details.

This repo contains the code for the HRI Flink Validation FHIR Job of the HRI, which validates that the incoming (HRI Record's) message payload adheres to the [FHIR specification](https://www.hl7.org/fhir/overview.html).

Note: This software uses Apache Flink (https://flink.apache.org/) for streaming data and is written in Scala (2.12.11).

## Communication
* Please [join](https://alvearie.io/contributions/requestSlackAccess/) our Slack channel for further questions: `#health-record-ingestion`
* Please see recent contributors or [maintainers](MAINTAINERS.md)
## Getting Started

### Prerequisites
* Java 1.8 - IBM requires the use of AdoptOpenJDK java distribution which you can download from [this site](https://adoptopenjdk.net/?variant=openjdk8) or install using a package manager like `homebrew` for mac
* Scala 2.12.11 - you can use an official [distribution](https://www.scala-lang.org/download/) or a package manager like `homebrew` for mac
* Java/Scala IDE (Optional) - we use IntelliJ, but it requires a licensed version.
* Ruby (Optional) - required for integration tests. See [testing](test/README.md) for more details.
* IBM Cloud CLI (Optional) - useful for local testing. Installation [instructions](https://cloud.ibm.com/docs/cli?topic=cloud-cli-getting-started).

### Building
From the base directory, run `./gradlew clean build`. This will download dependencies and run all the unit tests. (Some output has been omitted for the sake of concision)

This depends on the `hri-flink-pipeline-core` [GitHub repo](https://github.com/Alvearie/hri-flink-pipeline-core) and it's published packages.

```
hri-flink-validation-fhir % ./gradlew clean build

> Task :validator:test
Discovery starting.
Discovery completed in 186 milliseconds.
Run starting. Expected test count is: 10

FhirJsonValidationTest:
Run completed in 978 milliseconds.
Total number of tests run: 10
Suites: completed 2, aborted 0
Tests: succeeded 10, failed 0, canceled 0, ignored 0, pending 0
All tests passed.

> Task :validator:reportTestScoverage
[info] Found 1 subproject scoverage data directories [hri-flink-validation-fhir/validator/build/scoverage]

> Task :validator:reportScoverage
[info] Found 1 subproject scoverage data directories [/hri-flink-validation-fhir/validator/build/scoverage]
Scoverage report:
  hri-flink-validation-fhir/validator/build/reports/scoverage/index.html

> Task :validation:test
Discovery starting.
Discovery completed in 100 milliseconds.
Run starting. Expected test count is: 8
FhirValidationJobTest:
Run completed in 568 milliseconds.
Total number of tests run: 8
Suites: completed 2, aborted 0
Tests: succeeded 8, failed 0, canceled 0, ignored 0, pending 0
All tests passed.

> Task :validation:reportTestScoverage
[info] Found 1 subproject scoverage data directories [hri-flink-validation-fhir/validation/build/scoverage]

> Task :validation:reportScoverage
[info] Found 1 subproject scoverage data directories [hri-flink-validation-fhir/validation/build/scoverage]
Scoverage report:
  hri-flink-validation-fhir/validation/build/reports/scoverage/index.html

BUILD SUCCESSFUL in 54s
27 actionable tasks: 27 executed

```

## CI/CD
GitHub actions is used for CI/CD. It runs unit tests, builds the code, and then runs integration tests on our Flink cluster using Event Streams and an HRI Management API instance. 

Each branch uses its own topics, so different builds don't interfere with each other. Integration tests will clean up after themselves cancelling the Flink job, deleting the job jar, and the Event Streams topics.

The Flink logs are available for troubleshooting. They can be viewed in the Flink UI or the Kubernetes logs. The logs for all jobs are combined, so you may need to search to a specific time frame or look for specific keywords.


## Releases
Releases are created by creating Git tags, which trigger a Github actions build that publishes a release version in Github packages.

## Code Overview

### Classes
The code contains two subprojects: 
- A FHIR validator library - is in its own project so that it can be published and used by others who might want to use it in a custom validation job.
- A Flink FHIR validation job - uses the validator library; currently validates that the records meet the 'bundle' FHIR schema specification. A future goal is to support FHIR profile validation.

### Tests
This repository contains both unit tests and end-to-end integration tests.

- The validator and validation job have files containing respective unit tests
- The fhir.jobtest package within the Validation project contains examples of an end-to-end streaming job test that use the Flink Framework Unit Test support of the [MiniClusterWithClientResource](https://ci.apache.org/projects/flink/flink-docs-stable/dev/stream/testing.html#junit-rule-miniclusterwithclientresource)

#### Test Coverage
The HRI team requires that your code has at least 90% unit test code coverage. Anything less will likely not be accepted as a new contribution.

The build automatically creates a Scoverage Test Report that contains test coverage percentages and a coverage file at `hri-flink-validation-fhir/validator/build/reports/scoverage/index.html` and `hri-flink-validation-fhir/validation/build/reports/scoverage/index.html`. 

## Contribution Guide
Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.