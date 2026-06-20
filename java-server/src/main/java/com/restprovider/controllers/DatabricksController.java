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
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class DatabricksController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;
    private final HttpInvoker httpInvoker;

    public DatabricksController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run, DatabricksController::invokeHttp);
    }

    public DatabricksController(PasscodeValidator passcodeValidator,
                                CommandRunner commandRunner,
                                HttpInvoker httpInvoker) {
        super("Databricks");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
        this.httpInvoker = httpInvoker;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();

        if (!validatePassCode(request, response)) {
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "".equals(route)) {
            String projectName = HttpRequestUtil.headerValue(request, "projectName");
            String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));
            String sql = HttpRequestUtil.headerValue(request, "sql_statement").trim();
            String catalog = HttpRequestUtil.headerValue(request, "catalog");
            String schema = HttpRequestUtil.headerValue(request, "schema");
            String host = headerOrDefault(request, "serverHostName", env("RESTPROVIDER_DATABRICKS_HOST"));
            String warehouse = headerOrDefault(request, "sqlWarehouse", env("RESTPROVIDER_DATABRICKS_WAREHOUSE"));
            String token = env("databricks_pat");

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);
            String defaultOutput = testcaseName + "_databricks_query_result.json";
            String outputFile = headerOrDefault(request, "outputFile", defaultOutput);
            Path outputPath = tempFolder.resolve(outputFile);

            String payload = "{"
                    + "\"warehouse_id\":\"" + JsonUtil.escape(warehouse) + "\"," 
                    + "\"catalog\":\"" + JsonUtil.escape(catalog) + "\"," 
                    + "\"schema\":\"" + JsonUtil.escape(schema) + "\"," 
                    + "\"statement\":\"" + JsonUtil.escape(sql) + "\""
                    + "}";
            String endpoint = "https://" + host + "/api/2.0/sql/statements/";
            String result = httpInvoker.call("POST", endpoint, token, payload);
            Files.writeString(outputPath, result, StandardCharsets.UTF_8);

            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "run".equalsIgnoreCase(route)) {
            int limit = parseIntOrDefault(HttpRequestUtil.headerValue(request, "limitTo"), 20);
            String result = commandRunner.run("databricks", "runs list --output JSON --limit " + limit);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "job/run".equalsIgnoreCase(route)) {
            String jobId = HttpRequestUtil.headerValue(request, "jobId");
            String result = commandRunner.run("databricks", "jobs run-now --job-id " + jobId);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "warehouse/wake".equalsIgnoreCase(route)) {
            String host = headerOrDefault(request, "serverHostName", env("RESTPROVIDER_DATABRICKS_HOST"));
            String warehouse = headerOrDefault(request, "sqlWarehouse", env("RESTPROVIDER_DATABRICKS_WAREHOUSE"));
            String token = env("databricks_pat");
            String endpoint = "https://" + host + "/api/2.0/sql/warehouses/" + warehouse + "/start";
            String result = httpInvoker.call("POST", endpoint, token, "");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "cluster".equalsIgnoreCase(route)) {
            String clusterId = HttpRequestUtil.headerValue(request, "clusterId");
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

    private boolean validatePassCode(ClassicHttpRequest request, ClassicHttpResponse response) {
        String passCode = HttpRequestUtil.headerValue(request, "passCode");
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

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
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

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }

    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String bearerToken, String body);
    }
}

