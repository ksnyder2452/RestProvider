# Java Controller Status

This file tracks the Java implementation status for each controller.

## Status Definitions

- Native: implemented directly in Java controller and service code.
- N/A: no routes are defined for that controller.

| Controller | Route Count | Java Status | Notes |
|---|---:|---|---|
| AZPipeline | 4 | Native | Azure DevOps pipeline runs/list/show/wait flows implemented via Azure CLI with header/query aliases, runtime options, and passcode accepted from headers or query params |
| Azure | 2 | Native | Implemented in Java service layer |
| BrowserStack | 6 | Native | Native BrowserStack and Appium route set with output-file parity |
| Business | 0 | Native | Added Java business health endpoint |
| CosmosDB | 8 | Native | Native Cosmos route set implemented via Azure CLI with GET and POST query support, route aliases, runtime input overrides, and required-field validation |
| Databricks | 5 | Native | Native SQL statement/run/job/warehouse/cluster Databricks flows implemented |
| DataFactory | 4 | Native | Pipeline status/list/create-run/wait flows implemented via Azure CLI with route aliases, header/query input support, and required-field validation |
| Dataverse | 4 | Native | Native Dataverse route set implemented via SQL CLI with GET and POST query support, route aliases, runtime credential overrides, and required-field validation |
| FHIR | 4 | Native | Authenticated FHIR GET/POST/PUT/DELETE operations implemented via native HTTP with route-based resource paths, query aliases, token override support, and required-field validation |
| File | 31 | Native | Full file route set implemented including sorting, archive, sftp, local transfer, diff, and template-copy flows, with common routes accepting query-param aliases and required-file validation |
| Jenkins | 4 | Native | Native Jenkins GET/POST/PUT/DELETE pass-through with auth implemented |
| JMeter | 3 | Native | Native process-based jmeter version/server/script-run endpoints |
| LogAnalytics | 3 | Native | Native log analytics message matching routes implemented with route aliases plus query/header aliases for run, workspace, and expected-message inputs |
| Misc | 15 | Native | Native misc route set implemented for VPN, heartbeat, time, process, variables, random, and credential flows, with tested routes accepting query/header aliases and required-field validation |
| MSD | 1 | Native | Native sqlcmd-backed MSD query execution implemented |
| Office | 2 | Native | Native XLSX extraction via Apache POI with range aliases, query/header input parity, required-field validation, and standardized unauthorized responses |
| Oracle | 5 | Native | JDBC-based query, DDL, DML, and blob flows implemented with GET and POST query support, route aliases, runtime credential overrides, and required-field validation |
| OS | 11 | Native | Full OS route set implemented natively including folder, variable, and insightlink session scheduling aliases, query/header input parity, and required-field validation |
| Postman | 3 | Native | Native postman status and collection run orchestration with route aliases, passcode validation, runtime API-key override, and required-field validation |
| PowerBI | 4 | Native | Native authenticated PowerBI GET, POST, PUT, and DELETE pass-through implemented with request alias routes, runtime token and base-URL overrides, and required-field validation |
| Sequence | 4 | Native | XML-backed sequence create, delete, currval, and nextval flows implemented natively with create/delete/current/next aliases, query/header input parity, and required sequence-name validation |
| Snowflake | 1 | Native | Snowflake OAuth token retrieval implemented via native HTTP form post with oauth alias route, query/header aliases, runtime credential overrides, and required-field validation |
| SonarQube | 1 | Native | SonarQube GraphQL GET and POST support implemented via native HTTP client with graphql alias route, query/header aliases, runtime token override, and required-field validation |
| SQLServer | 5 | Native | JDBC-based query, DDL, DML, and blob flows implemented with GET and POST query support, route aliases, and required SQL and connection validation |
| StorageAccount | 10 | Native | Native Azure CLI-backed storage directory, blob, upload, download, and metadata routes implemented with selected route aliases, query/header input parity, and required-file validation on key flows |
| String | 8 | Native | Full route logic implemented in Java with compare, array, json, and hash aliases, query/header input parity, and required-field validation |
| Synapse | 1 | Native | Native sqlcmd-backed Synapse query execution implemented with GET and POST root or query alias support, runtime credential overrides, and required-field validation |
| Wait | 6 | Native | Sleep, status polling, synchronous, and asynchronous wait flows implemented with sleep route aliases, query-param passcode support on protected routes, and focused integration coverage |
