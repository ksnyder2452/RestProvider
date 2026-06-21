package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the Dataverse integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class DataverseController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public DataverseController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public DataverseController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("Dataverse");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
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
        String method = request.getMethod().toUpperCase();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("GET".equals(method) || "POST".equals(method))
                && ("".equals(route) || "query".equalsIgnoreCase(route))) {
            handleQuery(request, response, query);
            return;
        }
        if (("PUT".equals(method) || "POST".equals(method)) && "ddl".equalsIgnoreCase(route)) {
            handleExecuteNonQuery(request, response, query);
            return;
        }
        if (("PUT".equals(method) || "POST".equals(method)) && "dml".equalsIgnoreCase(route)) {
            handleExecuteNonQuery(request, response, query);
            return;
        }
        if (("PUT".equals(method) || "DELETE".equals(method) || "POST".equals(method))
                && ("".equals(route) || "nonquery".equalsIgnoreCase(route))) {
            handleExecuteNonQuery(request, response, query);
            return;
        }

        super.handle(request, response, subPath);
    }

    private void handleQuery(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "dataverse");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "dataverse_query"));
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        if (!require(response, "sql_statement", sql)) {
            return;
        }

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);
        String outputFile = defaultValue(readValue(request, query, "outputFile"), testcaseName + "_dataverse_query_result.txt");
        Path outputPath = tempFolder.resolve(outputFile);
        Files.deleteIfExists(outputPath);

        String result = runDataverseSql(request, query, sql);
        if (isFailure(result)) {
            respondJson(response, HttpStatus.SC_BAD_REQUEST,
                    "{\"connectionFailure\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        long rowCount = Arrays.stream(result.split("\\R")).filter(s -> !s.isBlank()).count();
        respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rowCount + "\"}");
    }

    private void handleExecuteNonQuery(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) {
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        if (!require(response, "sql_statement", sql)) {
            return;
        }
        String result = runDataverseSql(request, query, sql);
        if (isFailure(result)) {
            respondJson(response, HttpStatus.SC_BAD_REQUEST,
                    "{\"connectionFailure\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        String rowCount = extractRowsAffected(result);
        respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + JsonUtil.escape(rowCount) + "\"}");
    }

    private String runDataverseSql(ClassicHttpRequest request, Map<String, String> query, String sql) {
        String environment = readValue(request, query, "dv_environment", "environment", "org");
        if (environment.isBlank()) {
            return "Missing required parameter: dv_environment";
        }
        String user = defaultValue(readValue(request, query, "dv_user", "user"), env("dv_user"));
        String pwd = defaultValue(readValue(request, query, "dv_password", "password"), env("dv_password"));
        if (user.isBlank() || pwd.isBlank()) {
            return "Missing Dataverse credentials (dv_user/dv_password)";
        }

        String server = environment.contains(".") ? environment : environment + ".dynamics.com";
        String args = "-S \"" + server + "\" -U \"" + user + "\" -P \"" + pwd + "\" -Q \""
                + escapeQuotes(sql) + "\"";
        return commandRunner.run("sqlcmd", args);
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
        if (route.startsWith("dataverse/")) {
            route = route.substring("dataverse/".length());
        }
        if ("dataverse".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String safeName(String value) {
        return value == null ? "" : value.replace(" ", "_");
    }

    private static String escapeQuotes(String value) {
        return (value == null ? "" : value).replace("\"", "\\\"");
    }

    private static boolean isFailure(String output) {
        if (output == null) {
            return true;
        }
        String lower = output.toLowerCase();
        return lower.contains("error") || lower.contains("exception") || lower.contains("failed");
    }

    private static String extractRowsAffected(String output) {
        if (output == null || output.isBlank()) {
            return "0";
        }
        String[] lines = output.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("\\d+")) {
                return trimmed;
            }
        }
        return "1";
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

    private static boolean require(ClassicHttpResponse response, String field, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"" + JsonUtil.escape("Missing required parameter: " + field) + "\"}");
        return false;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
}



