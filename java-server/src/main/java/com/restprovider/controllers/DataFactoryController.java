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
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the DataFactory integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class DataFactoryController extends BaseController {
    private static final Pattern STATUS_PATTERN = Pattern.compile("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public DataFactoryController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public DataFactoryController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("DataFactory");
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
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "pipeline/status".equalsIgnoreCase(route)) {
            String runId = readValue(request, query, "pipelineRunId", "runId", "id");
            String expectedStatus = readValue(request, query, "expectedStatus", "status");
            if (!require(response, "pipelineRunId", runId) || !require(response, "expectedStatus", expectedStatus)) {
                return;
            }
            String output = runShow(runId, request, query);
            String status = extractField(STATUS_PATTERN, output);
            String message = extractField(MESSAGE_PATTERN, output);
            String content = "Pipeline status was correct (with associated message " + message + ")";
            int statusCode = HttpStatus.SC_OK;

            if (!status.equals(expectedStatus)) {
                content = "Pipeline status " + status + " (with associated message " + message
                        + ") did not match expected Status " + expectedStatus;
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            } else {
                String expectedMessage = readValue(request, query, "expectedMessage", "message");
                if (!expectedMessage.isBlank() && !message.startsWith(expectedMessage)) {
                    content = "Pipeline message " + message + " did not match expected Message " + expectedMessage;
                    statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
                }
            }

            respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
            && ("pipeline/run".equalsIgnoreCase(route) || "pipelines/run".equalsIgnoreCase(route))) {
            String finalPipelineName = headerOrEnv(request, query, "pipelineName", "RESTPROVIDER_DATAFACTORY_PIPELINE_NAME", "");
            String runId = readValue(request, query, "pipelineRunId", "referenceRunId", "runId");
            String factoryName = factoryName(request, query);
            String resourceGroup = resourceGroup(request, query);
            if (!require(response, "factoryName", factoryName)
                || !require(response, "resourceGroupName", resourceGroup)
                || !require(response, "pipelineName", finalPipelineName)) {
            return;
            }

            String output = runAz("datafactory pipeline create-run --factory-name \"" + factoryName
                    + "\" --name \"" + finalPipelineName + "\" --resource-group \""
                + resourceGroup + "\""
                + (runId.isBlank() ? "" : " --reference-pipeline-run-id \"" + runId + "\""));

            String result = trimRunId(output);
            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "datafactory");
            String testcaseName = readValue(request, query, "testcaseName", "testcase", "testCase");
            String outputFile = readValue(request, query, "outputFile");
            Path file = pipelineIdOutput(projectName,
                    outputFile.isBlank() ? (safeName(testcaseName) + "_pipelineid.txt") : outputFile);
            Files.createDirectories(file.getParent());
            Files.writeString(file, result, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("pipeline/run/waitfor".equalsIgnoreCase(route) || "pipeline/waitfor".equalsIgnoreCase(route))) {
            String runId = readValue(request, query, "pipelineRunId", "runId", "id");
            String expectedStatus = readValue(request, query, "expectedStatus", "status");
            String expectedMessage = readValue(request, query, "expectedMessage", "message");
            int sleepMs = parseIntOrDefault(readValue(request, query, "sleepInterval", "sleepMs", "sleepSeconds"), 30_000);
            if (!require(response, "pipelineRunId", runId) || !require(response, "expectedStatus", expectedStatus)) {
                return;
            }
            if (sleepMs < 1000) {
                sleepMs = sleepMs * 1000;
            }

            String status = "InProgress";
            String message = "";
            int guard = 0;
            while (!expectedStatus.contains(status) && guard < 240) {
                String output = runShow(runId, request, query);
                status = extractField(STATUS_PATTERN, output);
                message = extractField(MESSAGE_PATTERN, output);
                guard++;
                if (!expectedStatus.contains(status)) {
                    sleepQuietly(sleepMs);
                }
            }

            String content = "Pipeline status was correct (with associated message " + status + ")";
            int statusCode = HttpStatus.SC_OK;
            if (!status.equals(expectedStatus)) {
                content = "Pipeline status " + status + " (with associated status " + status
                        + ") did not match expected Status " + expectedStatus;
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            } else if (!expectedMessage.isBlank() && !message.startsWith(expectedMessage)) {
                content = "Pipeline message " + message + " did not match expected Message " + expectedMessage;
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            }

            respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && ("pipelines".equalsIgnoreCase(route) || "pipeline/list".equalsIgnoreCase(route))) {
            String factoryName = factoryName(request, query);
            String resourceGroup = resourceGroup(request, query);
            if (!require(response, "factoryName", factoryName) || !require(response, "resourceGroupName", resourceGroup)) {
                return;
            }
            String output = runAz("datafactory pipeline list --factory-name \"" + factoryName
                    + "\" --resource-group \"" + resourceGroup + "\"");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private String runShow(String runId, ClassicHttpRequest request, Map<String, String> query) {
        return runAz("datafactory pipeline-run show --factory-name \"" + factoryName(request, query)
                + "\" --resource-group \"" + resourceGroup(request, query) + "\" --run-id \""
                + runId + "\"");
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

    private String runAz(String args) {
        return commandRunner.run("az", args);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("datafactory/")) {
            route = route.substring("datafactory/".length());
        }
        return route;
    }

    private static String headerOrEnv(ClassicHttpRequest request, Map<String, String> query,
                                      String header, String env, String defaultValue) {
        String v = readValue(request, query, header);
        if (v != null && !v.isBlank()) {
            return v;
        }
        String envVal = System.getenv(env);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return defaultValue;
    }

    private static String factoryName(ClassicHttpRequest request, Map<String, String> query) {
        String value = readValue(request, query, "factoryName", "factory");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return headerOrEnv(request, query, "factoryName", "RESTPROVIDER_DATAFACTORY_FACTORY_NAME", "");
    }

    private static String resourceGroup(ClassicHttpRequest request, Map<String, String> query) {
        String value = readValue(request, query, "resourceGroupName", "resourceGroup", "rg");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return headerOrEnv(request, query, "resourceGroupName", "RESTPROVIDER_DATAFACTORY_RESOURCE_GROUP", "");
    }

    private static String trimRunId(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.trim();
        if (trimmed.contains(":")) {
            trimmed = trimmed.substring(trimmed.indexOf(':') + 1).trim();
        }
        trimmed = trimmed.replace("\"", "").trim();
        return trimmed;
    }

    private static String extractField(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input == null ? "" : input);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String safeName(String name) {
        return (name == null ? "pipeline" : name).replace(" ", "_");
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

    private static Path pipelineIdOutput(String projectName, String outputFile) {
        return Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, outputFile);
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}



