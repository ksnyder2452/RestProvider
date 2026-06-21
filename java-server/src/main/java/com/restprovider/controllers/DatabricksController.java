package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the Databricks integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class DatabricksController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;
    private final HttpInvoker httpInvoker;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public DatabricksController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run, DatabricksController::invokeHttp);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public DatabricksController(PasscodeValidator passcodeValidator,
                                CommandRunner commandRunner,
                                HttpInvoker httpInvoker) {
        super("Databricks");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
        this.httpInvoker = httpInvoker;
    }

    /**
     * Handles incoming HTTP requests for this controller's route surface.
     *
     * @param request inbound HTTP request
     * @param response outbound HTTP response
     * @param subPath controller-specific route segment after /api/{controller}/
     * @throws IOException when I/O work fails
     * @throws HttpException when request handling fails at HTTP protocol level
     */
    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))
                && ("".equals(route) || "sql/query".equalsIgnoreCase(route))) {
            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "databricks");
            String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "databricks_query"));
            String sql = readValue(request, query, "sql_statement", "sql", "query").trim();
            String catalog = readValue(request, query, "catalog");
            String schema = readValue(request, query, "schema");
            String host = defaultValue(readValue(request, query, "serverHostName", "host"), env("RESTPROVIDER_DATABRICKS_HOST"));
            String warehouse = defaultValue(readValue(request, query, "sqlWarehouse", "warehouseId", "warehouse"),
                    env("RESTPROVIDER_DATABRICKS_WAREHOUSE"));
            String token = defaultValue(readValue(request, query, "databricksToken", "token", "pat"), env("databricks_pat"));

            if (!require(response, "sql_statement", sql)
                    || !require(response, "serverHostName", host)
                    || !require(response, "sqlWarehouse", warehouse)
                    || !require(response, "databricksToken", token)) {
                return;
            }

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);
            String defaultOutput = testcaseName + "_databricks_query_result.json";
            String outputFile = defaultValue(readValue(request, query, "outputFile"), defaultOutput);
            Path outputPath = tempFolder.resolve(outputFile);

            String payload = "{"
                    + "\"warehouse_id\":\"" + JsonUtil.escape(warehouse) + "\"," 
                    + "\"catalog\":\"" + JsonUtil.escape(catalog) + "\"," 
                    + "\"schema\":\"" + JsonUtil.escape(schema) + "\"," 
                    + "\"statement\":\"" + JsonUtil.escape(sql) + "\""
                    + "}";
            String endpoint = "https://" + host + "/api/2.0/sql/statements/";
            // Outbound REST call to the target service endpoint.
            String result = httpInvoker.call("POST", endpoint, token, payload);
            Files.writeString(outputPath, result, StandardCharsets.UTF_8);

            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && ("run".equalsIgnoreCase(route) || "runs".equalsIgnoreCase(route))) {
            int limit = parseIntOrDefault(readValue(request, query, "limitTo", "limit"), 20);
            String result = commandRunner.run("databricks", "runs list --output JSON --limit " + limit);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "jobs".equalsIgnoreCase(route)) {
            int limit = parseIntOrDefault(readValue(request, query, "limitTo", "limit"), 20);
            String result = commandRunner.run("databricks", "jobs list --output JSON --limit " + limit);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("job/run".equalsIgnoreCase(route) || "jobs/run".equalsIgnoreCase(route))) {
            String jobId = readValue(request, query, "jobId", "id");
            if (!require(response, "jobId", jobId)) {
                return;
            }
            String result = commandRunner.run("databricks", "jobs run-now --job-id " + jobId);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("warehouse/wake".equalsIgnoreCase(route) || "warehouse/start".equalsIgnoreCase(route))) {
            String host = defaultValue(readValue(request, query, "serverHostName", "host"), env("RESTPROVIDER_DATABRICKS_HOST"));
            String warehouse = defaultValue(readValue(request, query, "sqlWarehouse", "warehouseId", "warehouse"),
                    env("RESTPROVIDER_DATABRICKS_WAREHOUSE"));
            String token = defaultValue(readValue(request, query, "databricksToken", "token", "pat"), env("databricks_pat"));
            if (!require(response, "serverHostName", host)
                    || !require(response, "sqlWarehouse", warehouse)
                    || !require(response, "databricksToken", token)) {
                return;
            }
            String endpoint = "https://" + host + "/api/2.0/sql/warehouses/" + warehouse + "/start";
            // Outbound REST call to the target service endpoint.
            String result = httpInvoker.call("POST", endpoint, token, "");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("cluster".equalsIgnoreCase(route) || "cluster/start".equalsIgnoreCase(route))) {
            String clusterId = readValue(request, query, "clusterId", "id");
            if (!require(response, "clusterId", clusterId)) {
                return;
            }
            String stateOutput = commandRunner.run("databricks", "clusters get --cluster-id " + clusterId);
            String result = "Cluster was already running";
            if (!stateOutput.contains("\"state\": \"RUNNING\",")) {
                result = commandRunner.run("databricks", "clusters start --cluster-id " + clusterId);
                int loops = 0;
                while (loops < 20) {
                    String poll = commandRunner.run("databricks", "clusters get --cluster-id " + clusterId);
                    if (poll.contains("\"state\": \"RUNNING\",")) {
                        break;
                    }
                    sleepQuietly(30_000);
                    loops++;
                }
            }
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private boolean validatePassCode(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) {
        String passCode = readValue(request, query, "passCode", "passcode");
        if (!passcodeValidator.isValid(passCode)) {
            logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
            respondJson(response, HttpStatus.SC_UNAUTHORIZED, "{\"passCodeResult\":\"Passcode failure\"}");
            return false;
        }
        return true;
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("databricks/")) {
            route = route.substring("databricks/".length());
        }
        if ("databricks".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String safeName(String value) {
        return value == null ? "" : value.replace(" ", "_");
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String readValue(ClassicHttpRequest request, Map<String, String> query, String... keys) {
        for (String key : keys) {
            String headerValue = HttpRequestUtil.headerValue(request, key);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    String value = entry.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean require(ClassicHttpResponse response, String field, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"" + JsonUtil.escape("Missing required parameter: " + field) + "\"}");
        return false;
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String invokeHttp(String method, String endpoint, String bearerToken, String body) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("Content-Type", "application/json");
            HttpRequest req;
            if (body == null || body.isBlank()) {
                req = builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
            } else {
                req = builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            }
            // Outbound REST call to the target service endpoint.
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "Databricks request failed: " + ex.getMessage();
        }
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String bearerToken, String body);
    }
}



