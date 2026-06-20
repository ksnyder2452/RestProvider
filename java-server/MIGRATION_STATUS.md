# Java Controller Status

This file tracks the Java implementation status for each controller.

## Status Definitions

- Native: implemented directly in Java controller and service code.
- N/A: no routes are defined for that controller.

| Controller | Route Count | Java Status | Notes |
|---|---:|---|---|
| AZPipeline | 4 | Native | Azure DevOps pipeline runs/list/show/wait flows implemented via Azure CLI |
| Azure | 2 | Native | Implemented in Java service layer |
| BrowserStack | 6 | Native | Native BrowserStack and Appium route set with output-file parity |
| Business | 0 | Native | Added Java business health endpoint |
| CosmosDB | 8 | Native | Native Cosmos route set implemented via Azure CLI with output parity |
| Databricks | 5 | Native | Native SQL statement/run/job/warehouse/cluster Databricks flows implemented |
| DataFactory | 4 | Native | Pipeline status/list/create-run/wait flows implemented via Azure CLI |
| Dataverse | 4 | Native | Native Dataverse route set implemented via SQL CLI with passcode parity |
| FHIR | 4 | Native | Authenticated FHIR GET/POST/PUT/DELETE operations implemented via native HTTP |
| File | 31 | Native | Full file route set implemented including sorting, archive, sftp, local transfer, diff, and template-copy flows |
| Jenkins | 4 | Native | Native Jenkins GET/POST/PUT/DELETE pass-through with auth implemented |
| JMeter | 3 | Native | Native process-based jmeter version/server/script-run endpoints |
| LogAnalytics | 3 | Native | Native log analytics message matching routes implemented |
| Misc | 15 | Native | Native misc route set implemented for VPN/heartbeat/time/process/variables/random/credential flows |
| MSD | 1 | Native | Native sqlcmd-backed MSD query execution implemented |
| Office | 2 | Native | Native XLSX extraction via Apache POI |
| Oracle | 5 | Native | JDBC-based query/DDL/DML/blob flows implemented |
| OS | 11 | Native | Full OS route set implemented natively including insightlink session scheduling |
| Postman | 3 | Native | Native postman status and collection run orchestration |
| PowerBI | 4 | Native | Native authenticated PowerBI GET/POST/PUT/DELETE pass-through implemented |
| Sequence | 4 | Native | XML-backed sequence create/delete/currval/nextval implemented natively |
| Snowflake | 1 | Native | Snowflake OAuth token retrieval implemented via native HTTP form post |
| SonarQube | 1 | Native | SonarQube GraphQL POST implemented via native HTTP client |
| SQLServer | 5 | Native | JDBC-based query/DDL/DML/blob flows implemented |
| StorageAccount | 10 | Native | Native Azure CLI-backed storage directory/blob/upload/download/metadata routes implemented |
| String | 8 | Native | Full route logic implemented in Java |
| Synapse | 1 | Native | Native sqlcmd-backed Synapse query execution implemented |
| Wait | 6 | Native | Sleep, status polling, synchronous and asynchronous wait flows implemented |
