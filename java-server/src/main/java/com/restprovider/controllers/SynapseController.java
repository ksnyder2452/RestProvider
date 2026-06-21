package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the Synapse integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class SynapseController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public SynapseController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public SynapseController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("Synapse");
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
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if (("GET".equals(method) || "POST".equals(method))
                && ("".equals(route) || "query".equalsIgnoreCase(route))) {
            if (!validatePassCode(request, response, query)) {
                return;
            }

            String projectName = readValue(request, query, "projectName", "project");
            String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testCase", "outputName"), "synapse"));
            String serverName = readValue(request, query, "serverName", "server", "synapseServer");
            String databaseName = readValue(request, query, "databaseName", "database", "dbName");
            String user = defaultValue(readValue(request, query, "userName", "user", "synapseUser"), env("azure_account"));
            String pwd = defaultValue(readValue(request, query, "password", "synapsePassword"), env("azure_pwd"));
            String sql = readValue(request, query, "sql_statement", "sql", "query");

            if (!require(response, "serverName", serverName)
                    || !require(response, "databaseName", databaseName)
                    || !require(response, "sql_statement", sql)) {
                return;
            }

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);
            String defaultName = testcaseName + "_synapse_query_result.txt";
            String outputFile = defaultValue(readValue(request, query, "outputFile", "resultFile", "fileName"), defaultName);
            Path outputPath = tempFolder.resolve(outputFile);

            String args = "-S" + serverName + " -d " + databaseName + " -G -U " + user
                    + " -P " + pwd + " -Q " + quote(sql) + " -o " + quote(outputPath.toString());
            String result = commandRunner.run("sqlcmd", args);
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
        if (route.startsWith("synapse/")) {
            route = route.substring("synapse/".length());
        }
        if ("synapse".equalsIgnoreCase(route)) {
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

    private static String readValue(ClassicHttpRequest request, Map<String, String> query, String... names) {
        for (String name : names) {
            String headerValue = HttpRequestUtil.headerValue(request, name);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
            String queryValue = query.get(name);
            if (queryValue != null && !queryValue.isBlank()) {
                return queryValue;
            }
        }
        return "";
    }

    private static String defaultValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean require(ClassicHttpResponse response, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"Missing required field: " + JsonUtil.escape(fieldName) + "\"}");
        return false;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\\\"")) + "\"";
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



