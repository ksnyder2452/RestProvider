# RestProvider

RestProvider is a multi-integration automation API server for test orchestration and utility workflows. The Java server under [java-server](java-server) now provides native implementations for every controller with defined routes.

## Repository Layout

- Java server: [java-server](java-server)
- Shell and container scripts: [ShellCommand](ShellCommand)
- Data files and templates: [data_files](data_files)
- Postman collections: [Postman_demos](Postman_demos)

## Controller Coverage

All controllers currently shipped in the Java server are implemented natively.

| Controller | Status |
|---|---|
| AZPipeline | Complete |
| Azure | Complete |
| BrowserStack | Complete |
| Business | Complete |
| CosmosDB | Complete |
| Databricks | Complete |
| DataFactory | Complete |
| Dataverse | Complete |
| FHIR | Complete |
| File | Complete |
| Jenkins | Complete |
| JMeter | Complete |
| LogAnalytics | Complete |
| Misc | Complete |
| MSD | Complete |
| Office | Complete |
| Oracle | Complete |
| OS | Complete |
| Postman | Complete |
| PowerBI | Complete |
| Sequence | Complete |
| Snowflake | Complete |
| SonarQube | Complete |
| SQLServer | Complete |
| StorageAccount | Complete |
| String | Complete |
| Synapse | Complete |
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

Representative routes:

1. String echo:
   - `GET /api/string/echo/{echoMe}`
2. Wait sleep:
   - `GET /api/wait/sleep/{seconds}`
3. Azure CLI login check:
   - `GET /api/azure/check/azurecli`
4. Business health:
   - `GET /api/business/health`

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

## API Testing With Postman

1. Import collection files from [Postman_demos](Postman_demos).
2. Set collection variables for host and required headers.
3. Execute smoke routes first, such as String, Wait, and Business endpoints.
4. Execute integration-specific routes after credentials or environment variables are configured.

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
