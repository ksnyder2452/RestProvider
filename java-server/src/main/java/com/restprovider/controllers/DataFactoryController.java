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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class DataFactoryController extends BaseController {
    private static final Pattern STATUS_PATTERN = Pattern.compile("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public DataFactoryController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public DataFactoryController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("DataFactory");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
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

        if ("GET".equalsIgnoreCase(method) && "pipeline/status".equalsIgnoreCase(route)) {
            String runId = HttpRequestUtil.headerValue(request, "pipelineRunId");
            String expectedStatus = HttpRequestUtil.headerValue(request, "expectedStatus");
            String output = runShow(runId, request);
            String status = extractField(STATUS_PATTERN, output);
            String message = extractField(MESSAGE_PATTERN, output);
            String content = "Pipeline status was correct (with associated message " + message + ")";
            int statusCode = HttpStatus.SC_OK;

            if (!status.equals(expectedStatus)) {
                content = "Pipeline status " + status + " (with associated message " + message
                        + ") did not match expected Status " + expectedStatus;
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            } else {
                String expectedMessage = HttpRequestUtil.headerValue(request, "expectedMessage");
                if (!expectedMessage.isBlank() && !message.startsWith(expectedMessage)) {
                    content = "Pipeline message " + message + " did not match expected Message " + expectedMessage;
                    statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
                }
            }

            respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "pipeline/run".equalsIgnoreCase(route)) {
            String finalPipelineName = headerOrEnv(request, "pipelineName", "RESTPROVIDER_DATAFACTORY_PIPELINE_NAME", "");
            String runId = HttpRequestUtil.headerValue(request, "pipelineRunId");
            String output = runAz("datafactory pipeline create-run --factory-name \"" + factoryName(request)
                    + "\" --name \"" + finalPipelineName + "\" --resource-group \""
                    + resourceGroup(request) + "\" --reference-pipeline-run-id \"" + runId + "\"");

            String result = trimRunId(output);
            String projectName = HttpRequestUtil.headerValue(request, "projectName");
            String testcaseName = HttpRequestUtil.headerValue(request, "testcaseName");
            String outputFile = HttpRequestUtil.headerValue(request, "outputFile");
            Path file = pipelineIdOutput(projectName,
                    outputFile.isBlank() ? (safeName(testcaseName) + "_pipelineid.txt") : outputFile);
            Files.createDirectories(file.getParent());
            Files.writeString(file, result, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "pipeline/run/waitfor".equalsIgnoreCase(route)) {
            String runId = HttpRequestUtil.headerValue(request, "pipelineRunId");
            String expectedStatus = HttpRequestUtil.headerValue(request, "expectedStatus");
            String expectedMessage = HttpRequestUtil.headerValue(request, "expectedMessage");
            int sleepMs = parseIntOrDefault(HttpRequestUtil.headerValue(request, "sleepInterval"), 30_000);
            if (sleepMs < 1000) {
                sleepMs = sleepMs * 1000;
            }

            String status = "InProgress";
            String message = "";
            int guard = 0;
            while (!expectedStatus.contains(status) && guard < 240) {
                String output = runShow(runId, request);
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

        if ("GET".equalsIgnoreCase(method) && "pipelines".equalsIgnoreCase(route)) {
            String output = runAz("datafactory pipeline list --factory-name \"" + factoryName(request)
                    + "\" --resource-group \"" + resourceGroup(request) + "\"");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private String runShow(String runId, ClassicHttpRequest request) {
        return runAz("datafactory pipeline-run show --factory-name \"" + factoryName(request)
                + "\" --resource-group \"" + resourceGroup(request) + "\" --run-id \""
                + runId + "\"");
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

    private static String headerOrEnv(ClassicHttpRequest request, String header, String env, String defaultValue) {
        String v = HttpRequestUtil.headerValue(request, header);
        if (v != null && !v.isBlank()) {
            return v;
        }
        String envVal = System.getenv(env);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return defaultValue;
    }

    private static String factoryName(ClassicHttpRequest request) {
        return headerOrEnv(request, "factoryName", "RESTPROVIDER_DATAFACTORY_FACTORY_NAME", "");
    }

    private static String resourceGroup(ClassicHttpRequest request) {
        return headerOrEnv(request, "resourceGroupName", "RESTPROVIDER_DATAFACTORY_RESOURCE_GROUP", "");
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

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}

