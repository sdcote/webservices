# Spring Boot Service Template

This is a repository template for deploying a Spring Boot service on a GCP Cloud Run serverless instance. It is designed to be a reusable repository template for deploying a container-based web service using GitHub Actions.

The entire CI/CD tech stack is designed to utilize free, open-source tools, but it can be easily modified to use proprietary, licensed COTS tools.


## Build Phase

The build phase of the delivery pipeline involves:
1. Compiling the Spring Boot application into a JAR
2. Conducting Unit Tests (JUnit and Jacoco code coverage report)
3. Conducting static code analysis (SpotBugs, PMD, Checkstyle)
4. Conducting software composition analysis (OWASP Dependency Check)
5. Building a Docker image of our Spring Boot web service and publishing it to our artifact repository.

Running `mvn install site` will compile the JAR, build the container image, run all unit tests, perform static code analysis, and generate reports.

Running `mvn deploy` will compile the JAR, build the container image, run all unit tests, build a Docker image, and publish the image to the artifact repository using the `artifactID` and `version` properties in the `pom.xml` file, provided all tests and code analysis pass. Note that the version is only maintained in <u>one</u> location: the `<version>`property in the `pom.xml` file. The version is accessible in the code, as can be evidenced in the `VersionController.java` file.

## Deploy Phase

After the build completes, the "deploy" phase is called to: 

1. Retrieve the official artifact from the artifact repository.
2. Deploy the retrieved artifact into the specified environment,
3. Optionally, run system tests appropriate for the current environment,
4. Optionally, call the "deploy" for the next environment (dev -> test-> -> qa -> prod)

Running the deployment workflow authenticates to the cloud service, retrieves the Docker image with the specified name and version, and deploys that image to the cloud platform. It also sets system properties that describe the deployment, allowing the deployed code to configure itself and operate correctly in that environment.

The `Build` workflow can be configured to call deployments serially so that deployments (and tests) in lower environments must complete successfully before subsequent deployments to higher environments are attempted. The `build.yml` workflow file shows how that is possible.


# Development Strategy
This section explains how the project is maintained, versioned, tested, and released.

## Versioning
The name of the project artifact reflects its version number. This project generates artifacts that are versioned using [https://semver.org/](https://semver.org/).  Given a version number MAJOR.MINOR.PATCH, increment the:

1. MAJOR version when you make incompatible API changes
1. MINOR version when you add functionality in a backward-compatible manner
1. PATCH version when you make backward-compatible bug fixes

Additional labels for pre-release and build metadata are available as extensions to the MAJOR.MINOR.PATCH format.

## Build Once; Deploy Many
The goal is to build artifacts once and deploy them everywhere as required. Only one official copy of each version of the project artifacts exists, and it is stored in the secure corporate artifact repository. Publication of deployment units are expected to only be performed by the system account running the build process so that to only way to publish a deployment unit into the official artifact repository is through the successful completion of the official build pipeline on an approved build service (e.g., GitHub Actions runner, Jenkins, Concourse) to ensure the integrity of the deployment unit.

For testing or production execution, all deployments retrieve deployment units only from the corporate artifact repository to ensure supply chain security.

All system testing will be performed by copying the official deployment unit from the corporate artifact repository onto the testing platform and conducting tests with that official version. 

The artifacts will **not be rebuilt** before any deployment and testing.

## Testing
As this is a library, the primary form of testing will be unit testing during the build process. These tests will be robust enough to ensure high confidence in the artifacts.

This project only builds, packages, unit tests, and delivers (publishes) the artifacts to the corporate repository. Integration testing is performed separately and is therefore in a separate project. 

System testing is expected to be performed in a separate deployment job that retrieves the artifact from the corporate artifact repository, deploys it in a separate test environment, and executes the required system testing to ensure the artifact functions as intended.

### Static Code Analysis

This project is configured to perform static analysis of the source code and any external dependencies. Static code analysis plugins generate reports that the team can use as part of the code review process. Metrics from these reports can be easily tracked, and when a new issue appears, it can be addressed and resolved before the code is approved and merged into the master branch or trunk of the source code repository. 

The Maven configuration uses the following plugins to perform static analysis:
* **Jacoco** – Unit Test Coverage,
* **SpotBugs** – Scan for code patterns indicating error idioms,
* **FindSecBugs** – Scan for common security-related code patterns,
* **OWASP Dependency Check** – Checks third-party libraries for known (security) errors,
* **PMD** – A code quality scanner looking for common poor coding practices.

The POM contains a reporting section with each of the plugin reports defined. The result is a set of HTML reports added to the project _site_ documentation. Run the following to build the code, run the tests, and generate source code metrics:

```bash
$ mvn install site
```

Look in the `<projectRoot>/target/site` directory for report files in HTML format. Open the `index.html` file in any browser and click on the Project Reports section to see the list of generated reports.

## Releases

Every build is a potential release candidate. Once unit tests are passed, the project artifact is published to the corporate artifact repository for subsequent retrieval and testing. These artifact builds are considered _**snapshot**_ releases and are not intended for production execution.

At some point, the project lead will decide to make a production release of the artifact. At that time, the version will be changed to the _**release**_ version in the source code, and the project will be rebuilt, unit tested, and delivered with the updated version. Note: The name of the artifact will not contain "-SNAPSHOT"; only the release version number will be used. This artifact will be published in the corporate artifact repository for other projects to use for deployment to execution environments.

The release version will then undergo system testing and be reviewed by other stakeholders. Once all tests pass, the project version in the source code will be updated to the next snapshot version, and development will continue with the new version. The _**release**_ version will remain in the corporate artifact repository. For example:

1. **1.0.0-SNAPSHOT** is developed, tested, and constantly over-written in the corporate artifact repository. If the artifact is changed, sufficiently, the semantic version may change while it is in active development. It may increment to version 1.1.0 during development, but it will retain its "**-SNAPSHOT**" suffix while it is under active development.
1. The project lead decides to release the artifact to deliver its functionality to other projects or users.
1. The "**-SNAPSHOT**" suffix is removed from the version of the project in the POM file. The code is then committed, pushed, built, unit-tested, and delivered to the artifact repository. At this point, the artifact is versioned as **1.0.0** and is considered "released" to the users and other projects. 
1. The source code version is incremented to **1.0.1-SNAPSHOT** (or another appropriate version number), code is committed, built, unit-tested, and delivered to the artifact repository.
1. The process repeats with step #1 above with version **1.0.1-SNAPSHOT**. Development continues with this snapshot version until another release is required.

It is expected that only released versions will be deployed into both production and non-production environments. Snapshot versions are considered unstable and are not to be used outside of development testing. Snapshot versions are not to be deployed.


### Continuous Deployment

Using snapshots can be eliminated, and the team can practice continuous deployment by ensuring the version number is incremented with every commit to the main branch and calling a deployment script from a deployment workflow that is triggered by the "build" workflow.

