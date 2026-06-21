# RestProvider Java Server

Java implementation of RestProvider controllers using Apache HttpComponents (HttpCore5).

## What this contains

- 32 Java controllers mirroring the existing controller names.
- Apache HTTP server bootstrap using `ServerBootstrap`.
- Native controller implementations for every route-bearing domain.
- DTO and service layer split for cleaner domain boundaries.
- Single control method to enable/disable controllers:
  - `ControllerRegistry#setControllerEnabled(String name, boolean isEnabled)`

## API routes

- Controller route: `/{api}/{controller}/...`
  - Example: `/api/azure`

## Route conventions

- When a section says `headers or query params supported`, the documented request inputs can be provided through either location.
- Protected routes generally accept either `passCode` or `passcode`.
- Alias routes are retained to preserve compatibility with older callers while exposing clearer route forms.

- Azure routes:
  - `POST /api/azure/az/extensions/config`
  - `GET /api/azure/check/azurecli`
  - `GET|POST /api/azure/az/command` (headers/query: `command` or `azCommand`)
- AZPipeline routes (headers or query params supported):
  - `GET /api/azpipeline/pipeline/runs`
  - `GET /api/azpipeline/pipeline/run`
  - `GET /api/azpipeline/pipeline/run/waituntilcomplete`
  - `POST /api/azpipeline/pipeline/run`
  - Supports aliases for organization, project, runId, pipeline name/id, branch, parameters, variables, and passcode
- BrowserStack routes (headers or query params supported):
  - `GET /api/browserstack/build/list`
  - `GET /api/browserstack/build/details`
  - `GET /api/browserstack/session/list`
  - `GET /api/browserstack/session/details`
  - `GET /api/browserstack/appium/build/list`
  - `GET /api/browserstack/appium/build/details`
  - `GET /api/browserstack/appium/session/list`
  - `GET /api/browserstack/appium/session/details`
- CosmosDB routes (headers or query params supported):
  - `GET|POST /api/cosmosdb` or `GET|POST /api/cosmosdb/query`
  - `GET /api/cosmosdb/databases`
  - `GET /api/cosmosdb/database/details`
  - `GET /api/cosmosdb/containers`
  - `GET /api/cosmosdb/container/details` (alias of `database/container/details`)
  - `GET /api/cosmosdb/database/container`
  - `GET /api/cosmosdb/database/container/match`
  - `GET /api/cosmosdb/database/container/matchonvalue`
- Databricks routes (headers or query params supported):
  - `GET|POST /api/databricks` (SQL statement run)
  - `GET|POST /api/databricks/sql/query` (alias)
  - `GET /api/databricks/run` or `GET /api/databricks/runs`
  - `GET /api/databricks/jobs`
  - `GET|POST /api/databricks/job/run` or `/api/databricks/jobs/run`
  - `GET|POST /api/databricks/warehouse/wake` or `/api/databricks/warehouse/start`
  - `GET|POST /api/databricks/cluster` or `/api/databricks/cluster/start`
- DataFactory routes (headers or query params supported):
  - `GET /api/datafactory/pipeline/status`
  - `GET|POST /api/datafactory/pipeline/run` (alias: `/api/datafactory/pipelines/run`)
  - `GET|POST /api/datafactory/pipeline/run/waitfor` (alias: `/api/datafactory/pipeline/waitfor`)
  - `GET /api/datafactory/pipelines` (alias: `/api/datafactory/pipeline/list`)
- Dataverse routes (headers or query params supported):
  - `GET|POST /api/dataverse` (SQL query)
  - `GET|POST /api/dataverse/query` (alias)
  - `PUT|POST /api/dataverse/ddl`
  - `PUT|POST /api/dataverse/dml`
  - `PUT|POST|DELETE /api/dataverse/nonquery` (and root route compatibility)
- FHIR routes (headers or query params supported):
  - `GET|POST|PUT|DELETE /api/fhir` (using `objectType`/`resourceType`)
  - `GET|POST|PUT|DELETE /api/fhir/query` (alias)
  - `GET|POST|PUT|DELETE /api/fhir/{resourceType}/{id...}` (route-based resource path)
  - Supports `fhirPaaSService` or `fhirBaseUrl`, plus `fhirToken` override (env fallback: `fhir_token`)
- Jenkins routes (headers or query params supported):
  - `GET|POST|PUT|DELETE /api/jenkins`
  - Supports `subURI` plus either `serverName`/`serverPort` or `jenkinsBaseUrl`
  - Supports runtime credential override: `jenkinsUser` and `jenkinsApiToken` (env fallback available)
- JMeter routes (headers or query params supported):
  - `GET /api/jmeter/version` (alias: `/api/jmeter/info/version`)
  - `GET /api/jmeter/servers` (alias: `/api/jmeter/server/list`)
  - `GET|POST /api/jmeter` (alias: `/api/jmeter/run`)
  - Script execution accepts aliases for script and output settings (`scriptName`, `resultFile`, `jmeterLogFile`)
- Javadoc routes:
  - `GET /api/javadoc/generate` (aliases: `/api/javadoc`, `/api/javadoc/build`) to generate project Javadocs via Maven
  - `GET /api/javadoc/docs` serves generated `target/reports/apidocs/index.html`
  - `GET /api/javadoc/docs/{relativePath}` serves generated documentation assets and pages
- K6 routes (headers or query params supported):
  - `GET /api/k6/version` (alias: `/api/k6/info/version`)
  - `GET|POST /api/k6` (alias: `/api/k6/run`)
  - Script execution accepts aliases for script and output settings (`scriptName`, `resultFile`)
- LogAnalytics routes (headers or query params supported):
  - `GET /api/loganalytics/message`
  - `GET /api/loganalytics/message/startswith` (alias: `/api/loganalytics/message/starts-with`)
  - `GET /api/loganalytics/message/endswith` (alias: `/api/loganalytics/message/ends-with`)
  - Supports aliases for run/workspace/expected message values via query params
- MSD routes (headers or query params supported):
  - `GET|POST /api/msd`
  - `GET|POST /api/msd/query` (alias)
  - Supports aliases for SQL/server/user/password parameters
- Office routes (headers or query params supported):
  - `GET /api/office/excel/all`
  - `GET /api/office/excel/bycoordinate` (aliases: `/api/office/excel/by-coordinate`, `/api/office/excel/range`)
  - Supports aliases for input file/sheet and coordinate parameters
- Oracle routes (headers or query params supported):
  - `GET|POST /api/oracle` (alias: `/api/oracle/query`)
  - `PUT|POST /api/oracle/ddl`
  - `PUT|POST /api/oracle/dml` (root route compatibility retained)
  - `DELETE /api/oracle/nonquery`
  - `GET /api/oracle/blob` (alias: `/api/oracle/blob/download`)
- Postman routes (headers or query params supported):
  - `GET /api/postman/status` (alias: `/api/postman/login/status`)
  - `GET|POST /api/postman/run/id` (alias: `/api/postman/collection/run/id`)
  - `GET|POST /api/postman/run/file` (alias: `/api/postman/collection/run/file`)
  - Supports passcode and runtime `postmanApiKey` override
- PowerBI routes (headers or query params supported):
  - `GET|POST|PUT|DELETE /api/powerbi` (alias: `/api/powerbi/request`)
  - Supports aliases for request path, organization, API version, base URL, and access token override
- Grafana routes (headers or query params supported):
  - `GET|POST|PUT|DELETE /api/grafana` (alias: `/api/grafana/request`)
  - Supports aliases for request path, API version, base URL, and API token override
- Snowflake routes (headers or query params supported):
  - `GET|POST /api/snowflake/token` (alias: `/api/snowflake/oauth/token`)
  - Supports aliases for token endpoint, scope, client credentials, and runtime user/password overrides
- SonarQube routes (headers or query params supported):
  - `GET|POST /api/sonarqube` (alias: `/api/sonarqube/graphql`)
  - Supports GraphQL query aliases, base URL override, and runtime API token override
- SQLServer routes (headers or query params supported):
  - `GET|POST /api/sqlserver` (alias: `/api/sqlserver/query`)
  - `PUT|POST /api/sqlserver/ddl`
  - `PUT|POST /api/sqlserver/dml` (root route compatibility retained)
  - `DELETE /api/sqlserver/nonquery`
  - `GET /api/sqlserver/blob` (alias: `/api/sqlserver/blob/download`)
- StorageAccount routes (headers or query params supported):
  - `GET /api/storageaccount/container/directories` (alias: `/api/storageaccount/directories`)
  - `GET /api/storageaccount/container/blobs` (alias: `/api/storageaccount/blobs`)
  - `PUT /api/storageaccount/datafile/upload`
  - `PUT /api/storageaccount/datafolder/upload` and `/datafolder/upload2`
  - `GET /api/storageaccount/datafile`, `/datafile2`, `/datafile/download`, `/datafile/metadata`
- Business route:
  - `GET /api/business/health`
  - `BusinessController` is intentionally a customization template.
  - Replace `src/main/java/com/restprovider/controllers/BusinessController.java` with your deployment-specific business APIs while keeping the same controller/file structure.
- String routes (native):
  - `GET /api/string/echo/{echoMe}`
  - `GET /api/string/echo2/{echoMe}`
  - `GET /api/string/isnumber` (alias: `/api/string/is-number`)
  - `GET /api/string/compare`
  - `GET /api/string/array/match` (alias: `/api/string/array/compare`)
  - `GET /api/string/json/to/array` (alias: `/api/string/json/array`)
  - `GET /api/string/encrypt`
  - `GET /api/string/hash` (alias: `/api/string/sha256`)
  - Supports query/header aliases for compare, hash, array, and JSON extraction inputs
- OS routes:
  - `GET /api/os/year`
  - `GET /api/os/month`
  - `GET /api/os/time`
  - `GET /api/os/ip`
  - `POST /api/os/folder` (alias: `/api/os/folder/create`)
  - `GET /api/os/folder` (alias: `/api/os/folder/root`)
  - `DELETE /api/os/folder/contents` (alias: `/api/os/folder/clean`)
  - `GET /api/os/variable` (alias: `/api/os/env/variable`)
  - `POST /api/os/variable` (alias: `/api/os/env/variable`)
  - `GET /api/os/crontab`
  - `POST /api/os/insightlink/session/schedule` (alias: `/api/os/session/schedule`)
  - Supports query/header aliases for project, folder, variable, schedule, and passcode inputs
- Sequence routes:
  - `POST /api/sequence` (alias: `/api/sequence/create`)
  - `DELETE /api/sequence` (alias: `/api/sequence/delete`)
  - `GET /api/sequence/currval` (alias: `/api/sequence/current`)
  - `GET /api/sequence/nextval` (alias: `/api/sequence/next`)
  - Supports query/header aliases for project, sequence name, recreate, and increment settings
- Synapse routes (headers or query params supported):
  - `GET|POST /api/synapse` (SQL query)
  - `GET|POST /api/synapse/query` (alias)
  - Supports aliases for project, server, database, SQL statement, output file, passcode, and runtime credential overrides
- File routes (headers or query params supported on common operations):
  - `GET /api/file/exists`
  - `GET /api/file/local` (alias: `/api/file/download/local`)
  - `POST /api/file/local` (alias: `/api/file/upload/local`)
  - Supports query/header aliases for passcode, project, folder/path, and file name on the documented routes above
- Misc routes (headers or query params supported on common operations):
  - `GET /api/misc/check/vpn`
  - `GET /api/misc/heartbeat`
  - `GET /api/misc/time/diff`
  - `GET /api/misc/file/property`, `GET /api/misc/data/variable`, `GET /api/misc/account/names`
  - Supports query/header aliases for passcode, expected network, host name, and time-diff inputs
- Wait routes:
  - `GET|POST /api/wait/sleep`
  - `GET /api/wait/sleep/{seconds}`
  - `GET /api/wait/waitforstatus`, `GET /api/wait/waitfordifferentstatus`
  - `POST /api/wait/waitfor/invoke`, `POST /api/wait/wait/until/state/synchronous`, `POST /api/wait/wait/until/state/asynchronous`
  - Protected wait routes accept passcode from either headers or query params
- List controller states:
  - `GET /admin/controllers`
- Enable or disable one controller:
  - `PUT /admin/controllers/{name}/enabled?value=true`
  - `PUT /admin/controllers/{name}/enabled?value=false`

## Architecture Notes

- Controller registry and dispatcher:
  - `src/main/java/com/restprovider/core/ControllerRegistry.java`
  - `src/main/java/com/restprovider/core/ControllerDispatcher.java`
- Domain DTO/service structure:
  - `src/main/java/com/restprovider/domain/azure/`
  - `src/main/java/com/restprovider/domain/business/`
  - `src/main/java/com/restprovider/domain/common/`
  - `src/main/java/com/restprovider/domain/security/`

## Install and build

### Windows (PowerShell)

```powershell
cd java-server\install
.\install.ps1
```

### Linux/macOS (bash)

```bash
cd java-server/install
chmod +x install.sh
./install.sh
```

## Run

```bash
java -jar target/restprovider-java-server-1.0.0.jar
```

Set a custom port with `RESTPROVIDER_PORT`.

## Migration Status

See [MIGRATION_STATUS.md](MIGRATION_STATUS.md) for controller-by-controller implementation status.

## Test

```bash
mvn -q clean test
```

Integration tests cover:

- Controller enable/disable admin flow.
- Routing parity (known/unknown route handling).
- Azure controller passcode and response behavior.
- Native controller behavior across the migrated route surface.
