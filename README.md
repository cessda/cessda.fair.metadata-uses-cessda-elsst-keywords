[![SQAaaS badge shields.io](https://img.shields.io/badge/sqaaas%20software-bronze-e6ae77)](https://api.eu.badgr.io/public/assertions/Er-WrIjbRYur_tYkmS3RtQ "SQAaaS bronze badge achieved")

# metadata-uses-cessda-elsst-keywords

This repository contains the source code for the CESSDA community-specific FAIR test MetadataUsesCessdaElsstKeywords.

## Prerequisites

Java 21 or greater is required to build and run this application.

## Quick Start

1. Check prerequisites and install any required software.
2. Clone the repository to your local workspace.
3. Build the application using `mvn clean verify`.
4. Run the application using the following command: `mvn -Dexec.mainClass=cessda.fairtests.MetadataUsesCessdaElsstKeywords -Dexec.args="<CDC URL>" org.codehaus.mojo:exec-maven-plugin:3.1.0:java`.

## Project Structure

This project uses the standard Maven project structure.

``` text
<ROOT>
├── pom.xml
└── Dockerfile
├── src                 # Contains all source code and assets for the application.
|   ├── main
|   |   ├── java        # Contains release source code of the application.
|   |   └── resources   # Contains release resources assets.
|   └── test
|       ├── java        # Contains test source code.
|       └── resources   # Contains test resource assets.
└── target              # The output directory for the build.
```

## Description

See the [Description](Description.md) file.

## Contributing

Please read [CONTRIBUTING](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

See [Semantic Versioning](https://semver.org/) for guidance.

## Contributors

You can find the list of contributors in the [CONTRIBUTORS](CONTRIBUTORS.md) file.

## License

See the [LICENSE](LICENSE.txt) file.

## CITING

See the [CITATION](CITATION.cff) file.
