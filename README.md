# RestProvider

[![Java Tests (push)](https://github.com/ksnyder2452/RestProvider/actions/workflows/java-tests.yml/badge.svg?event=push)](https://github.com/ksnyder2452/RestProvider/actions/workflows/java-tests.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE.md)

RestProvider is a multi-integration automation API server for test orchestration and utility workflows. The Java server under [java-server](java-server) now provides native implementations for every controller with defined routes.

## Repository Layout

- Java server: [java-server](java-server)
- Data files and templates: [data_files](data_files)

## Repository Structure

```
RestProvider/
├── java-server/                        # Java Spring Boot API server
│   ├── pom.xml                         # Maven build descriptor
│   ├── README.md                       # Server-specific documentation
│   ├── MIGRATION_STATUS.md             # Controller implementation status
│   ├── install/
│   │   ├── install.ps1                 # Windows install script
│   │   └── install.sh                  # Linux/macOS install script
│   └── src/
│       ├── main/
│       │   ├── java/com/restprovider/
│       │   │   ├── App.java            # Application entry point
│       │   │   ├── controllers/        # One class per integration controller
│       │   │   │   └── JavadocController.java  # Generates and serves Javadocs
│       │   │   ├── core/               # Server bootstrap, registry, dispatcher
│       │   │   ├── domain/             # DTOs and service layer (azure, business, common, security)
│       │   │   └── util/               # Shared utilities (JsonUtil)
│       │   └── resources/
│       │       └── log4j2.xml          # Logging configuration
│       └── test/
│           └── java/com/restprovider/
│               └── integration/        # Integration tests per controller
│                   └── JavadocControllerIntegrationTest.java
├── data_files/                         # Sample properties and response templates
│   ├── variable.properties
│   ├── sample_var.properties
│   └── templates/                      # Example request/response payloads
├── scripts/
│   └── sync-controller-status.ps1      # Utility to sync controller status docs
├── CONTRIBUTING.md
├── LICENSE.md
└── README.md
```

CSV sorting, column projection, and Log Analytics querying are implemented directly in the Java server.

## Controller Coverage

All controllers currently shipped in the Java server are implemented natively.

### Cloud and Platform Integrations

| Controller | Status |
|---|---|
| AZPipeline | Complete |
| Azure | Complete |
| CosmosDB | Complete |
| Databricks | Complete |
| DataFactory | Complete |
| Dataverse | Complete |
| FHIR | Complete |
| StorageAccount | Complete |
| Synapse | Complete |

### DevOps and Test Tooling

| Controller | Status |
|---|---|
| BrowserStack | Complete |
| Jenkins | Complete |
| JMeter | Complete |
| K6 | Complete |
| Postman | Complete |
| SonarQube | Complete |

### Data and Analytics Services

| Controller | Status |
|---|---|
| MSD | Complete |
| Oracle | Complete |
| PowerBI | Complete |
| Grafana | Complete |
| Snowflake | Complete |
| SQLServer | Complete |

### System and Utility Controllers

| Controller | Status |
|---|---|
| Business | Complete |
| File | Complete |
| LogAnalytics | Complete |
| Misc | Complete |
| Office | Complete |
| OS | Complete |
| Javadoc | Complete |
| Sequence | Complete |
| String | Complete |
| Wait | Complete |

Detailed controller notes are maintained in [java-server/MIGRATION_STATUS.md](java-server/MIGRATION_STATUS.md).

## Sample Use Cases

1. Validate service availability and request wiring.
   - Call a String echo endpoint and verify the returned payload.
2. Perform assertions in test pipelines.
   - Use String endpoints to check numeric content, comparisons, hashes, and matching behavior.
3. Execute environment and file automation.
   - Use OS, File, and Wait endpoints for directory management, scheduling, polling, and local utility flows.
4. Orchestrate external platforms behind one REST surface.
   - Use Azure, Jenkins, PowerBI, CosmosDB, BrowserStack, Databricks, Oracle, SQLServer, and related controllers from a single API host.
5. Temporarily disable risky integrations without changing deployments.
   - Use the admin controller-toggle endpoints to enable or disable individual controllers at runtime.

## Quick Start

Deployment recommendation:

1. Run RestProvider Server on an isolated host, such as a dedicated VM or Docker container.
2. This is recommended because several controllers can interact with the local operating system, files, processes, and other environment-level resources.
3. Avoid running the server directly on shared developer workstations or sensitive hosts unless access is tightly controlled.

Prerequisites:

1. Java 17+
2. Maven 3.9+

Build and run:

1. Install dependencies and package the server.
   - Windows PowerShell:
     - `cd java-server\install`
     - `./install.ps1`
   - Linux or macOS:
     - `cd java-server/install`
     - `chmod +x install.sh`
     - `./install.sh`
2. Start the server.
   - `cd java-server`
   - `java -jar target/restprovider-java-server-1.0.0.jar`

Port configuration:

1. Default port is `8080`.
2. Set `RESTPROVIDER_PORT` to override the listener port.

Route shape:

1. `/api/{controller}/...`
2. Example: `/api/azure`

Route conventions:

1. Most generalized controllers accept request inputs from headers, query parameters, or both.
2. Protected routes typically accept either `passCode` or `passcode`.
3. Alias routes are preserved for backward compatibility while newer, clearer route forms are added.

Representative routes:

1. Platform and cloud:
   - `GET|POST /api/azure/az/command`
   - `GET /api/azpipeline/pipeline/runs`, `POST /api/azpipeline/pipeline/run`
   - `GET|POST /api/cosmosdb` and `GET /api/cosmosdb/container/details`
   - `GET|POST /api/databricks/sql/query`, `GET /api/databricks/jobs`
   - `GET /api/datafactory/pipeline/status`, `POST /api/datafactory/pipeline/run`
   - `GET|POST|PUT|DELETE /api/fhir` and `/api/fhir/{resourceType}/{id...}`
   - `GET /api/storageaccount/directories`, `GET|POST /api/synapse/query`
2. DevOps and tooling:
   - `GET /api/browserstack/build/list`, `GET /api/browserstack/appium/session/list`
   - `GET|POST|PUT|DELETE /api/jenkins`
   - `GET /api/jmeter/version`, `GET|POST /api/postman/run/id`
   - `GET|POST /api/sonarqube/graphql`
3. Data and analytics:
   - `GET|POST /api/dataverse/query`, `PUT /api/dataverse/dml`
   - `GET|POST /api/msd/query`
   - `GET /api/office/excel/all`, `GET /api/office/excel/range`
   - `GET|POST /api/oracle/query`, `GET /api/oracle/blob`
   - `GET|POST|PUT|DELETE /api/powerbi/request`
   - `GET|POST /api/snowflake/oauth/token`
   - `GET|POST /api/sqlserver/query`, `GET /api/sqlserver/blob`
4. System and utility:
   - `GET /api/business/health`
   - `GET /api/file/exists`, `POST /api/file/upload/local`
   - `GET /api/javadoc/generate`, `GET /api/javadoc/docs`
   - `GET /api/loganalytics/message`, `GET /api/loganalytics/message/startswith`
   - `GET /api/misc/check/vpn`, `GET /api/misc/account/names`
   - `POST /api/os/session/schedule`, `GET /api/os/env/variable`
   - `POST /api/sequence/create`, `GET /api/sequence/current`
   - `GET /api/string/echo/{echoMe}`, `GET /api/string/sha256`
   - `GET /api/wait/sleep/{seconds}`, `POST /api/wait/wait/until/state/synchronous`

Route details and controller-specific aliases are documented in [java-server/README.md](java-server/README.md).

## Generated API Docs

Generated Javadocs are published by the Java server at:

- `GET /api/javadoc/docs` (or `http://localhost:8080/api/javadoc/docs` when running locally)

Important:

1. The `Javadoc` controller is disabled by default at startup.
2. Enable it before using documentation routes:
   - `PUT /admin/controllers/javadoc/enabled?value=true`

## Enable or Disable Controllers

The Java server includes one central control method in [java-server/src/main/java/com/restprovider/core/ControllerRegistry.java](java-server/src/main/java/com/restprovider/core/ControllerRegistry.java):

- `setControllerEnabled(String name, boolean isEnabled)`

Operational admin endpoints:

1. List all controller states.
   - `GET /admin/controllers`
2. Enable one controller.
   - `PUT /admin/controllers/{name}/enabled?value=true`
3. Disable one controller.
   - `PUT /admin/controllers/{name}/enabled?value=false`

Default startup state:

1. All registered controllers are disabled by default when the Java server starts.
2. Enable only the controllers you need using the admin endpoints above.

Expected behavior:

1. Enabled controllers continue to serve requests normally.
2. Disabled controllers return HTTP `403` with a controller-disabled message.

## Java Architecture

Core components:

1. Controller registry and dispatcher.
   - [java-server/src/main/java/com/restprovider/core/ControllerRegistry.java](java-server/src/main/java/com/restprovider/core/ControllerRegistry.java)
   - [java-server/src/main/java/com/restprovider/core/ControllerDispatcher.java](java-server/src/main/java/com/restprovider/core/ControllerDispatcher.java)
2. Native controller implementations.
   - [java-server/src/main/java/com/restprovider/controllers](java-server/src/main/java/com/restprovider/controllers)
3. Domain services and DTO layers.
   - [java-server/src/main/java/com/restprovider/domain](java-server/src/main/java/com/restprovider/domain)
4. Unified Log4j2 logging configuration.
   - [java-server/src/main/resources/log4j2.xml](java-server/src/main/resources/log4j2.xml)

## Validation

Build and test the Java server:

1. `cd java-server`
2. `mvn -q clean test`
3. `mvn -q clean package`

Useful integration test entry points include:

1. [java-server/src/test/java/com/restprovider/integration/AdminToggleIntegrationTest.java](java-server/src/test/java/com/restprovider/integration/AdminToggleIntegrationTest.java)
2. [java-server/src/test/java/com/restprovider/integration/RoutingParityIntegrationTest.java](java-server/src/test/java/com/restprovider/integration/RoutingParityIntegrationTest.java)
3. [java-server/src/test/java/com/restprovider/integration/AzureControllerIntegrationTest.java](java-server/src/test/java/com/restprovider/integration/AzureControllerIntegrationTest.java)

## Current Status

1. Every controller with defined routes is implemented natively in Java.
2. Controllers with no defined route surface have been removed from the shipped Java controller set.
3. The root controller coverage table and [java-server/MIGRATION_STATUS.md](java-server/MIGRATION_STATUS.md) are aligned with the current controller set.
