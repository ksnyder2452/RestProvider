# RestProvider Java Server

Java implementation of RestProvider controllers using Apache HttpComponents (HttpCore5).

## What this contains

- 31 Java controllers mirroring the existing controller names.
- Apache HTTP server bootstrap using `ServerBootstrap`.
- Native controller implementations for every route-bearing domain.
- DTO and service layer split for cleaner domain boundaries.
- Single control method to enable/disable controllers:
  - `ControllerRegistry#setControllerEnabled(String name, boolean isEnabled)`

## API routes

- Controller route: `/{api}/{controller}/...`
  - Example: `/api/azure`
- Azure routes:
  - `POST /api/azure/az/extensions/config`
  - `GET /api/azure/check/azurecli`
- Business route:
  - `GET /api/business/health`
- String routes (native):
  - `GET /api/string/echo/{echoMe}`
  - `GET /api/string/echo2/{echoMe}`
  - `GET /api/string/isnumber`
  - `GET /api/string/compare`
  - `GET /api/string/array/match`
  - `GET /api/string/json/to/array`
  - `GET /api/string/encrypt`
  - `GET /api/string/hash`
- OS routes:
  - `GET /api/os/year`
  - `GET /api/os/month`
  - `GET /api/os/time`
  - `GET /api/os/ip`
  - `POST /api/os/folder`
  - `GET /api/os/folder`
  - `DELETE /api/os/folder/contents`
  - `GET /api/os/variable`
  - `POST /api/os/variable`
  - `GET /api/os/crontab`
  - `POST /api/os/insightlink/session/schedule`
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
