package com.restprovider.controllers;

import com.restprovider.core.BaseController;
import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the K6 integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class K6Controller extends BaseController {
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public K6Controller() {
        this(ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public K6Controller(CommandRunner commandRunner) {
        super("K6");
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
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if ("GET".equalsIgnoreCase(method)
                && ("version".equalsIgnoreCase(route) || "info/version".equalsIgnoreCase(route))) {
            String output = commandRunner.run("k6", "version");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("".equals(route) || "run".equalsIgnoreCase(route))) {
            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "k6");
            String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "run"));
            String scriptName = readValue(request, query, "scriptName", "script", "testScript");
            String resultFile = defaultValue(readValue(request, query, "resultFile", "summaryFile", "result"), "summary.json");

            if (!require(response, "scriptName", scriptName)) {
                return;
            }

            String rootDir = System.getProperty("user.dir");
            Path scriptPath = Path.of(rootDir, "data_files", scriptName);
            Path tempFolder = Path.of(rootDir, "data_files", "temp", projectName);
            String resolvedResultFile = tempFolder.resolve(testcaseName + "_" + safeName(resultFile)).toString();

            String args = "run " + scriptPath + " --summary-export " + resolvedResultFile;
            String output = commandRunner.run("k6", args);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("k6/")) {
            route = route.substring("k6/".length());
        }
        if ("k6".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String safeName(String name) {
        return (name == null ? "" : name).replace(" ", "_");
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


