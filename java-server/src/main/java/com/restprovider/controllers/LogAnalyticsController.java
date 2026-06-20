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
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class LogAnalyticsController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;
    private final FileContentReader fileContentReader;

    public LogAnalyticsController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run,
                path -> Files.readString(path, StandardCharsets.UTF_8));
    }

    public LogAnalyticsController(PasscodeValidator passcodeValidator,
                                  CommandRunner commandRunner,
                                  FileContentReader fileContentReader) {
        super("LogAnalytics");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
        this.fileContentReader = fileContentReader;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            super.handle(request, response, subPath);
            return;
        }

        if (!("message".equalsIgnoreCase(route)
                || "message/startswith".equalsIgnoreCase(route)
                || "message/endswith".equalsIgnoreCase(route)
                || "message/starts-with".equalsIgnoreCase(route)
                || "message/ends-with".equalsIgnoreCase(route))) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response, query)) {
            return;
        }

        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "loganalytics");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "run"));
        String expectedMessage = readValue(request, query, "expectedMessage", "message", "expected");
        String runId = readValue(request, query, "runId", "run", "pipelineRunId");
        String workspaceId = defaultValue(readValue(request, query, "analyticsWorkspaceId", "workspaceId", "workspace"),
                env("RESTPROVIDER_LOGANALYTICS_WORKSPACE_ID"));
        if (!require(response, "runId", runId)
                || !require(response, "analyticsWorkspaceId", workspaceId)
                || !require(response, "expectedMessage", expectedMessage)) {
            return;
        }

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);
        String outputFile = defaultValue(readValue(request, query, "outputFile"), testcaseName + "_loganalytics.out");
        Path outputPath = tempFolder.resolve(outputFile);

        Path scriptPath = Path.of(System.getProperty("user.dir"), "ShellCommand", "AdfLogger.sh");
        String args = runId + " " + workspaceId + " " + quote(tempFolder.toString()) + " " + quote(outputFile);
        commandRunner.run(scriptPath.toString(), args);

        String result = fileContentReader.read(outputPath).trim();
        String content = result;
        int statusCode = HttpStatus.SC_OK;

        if ("message".equalsIgnoreCase(route)) {
            if (!result.contains("\"Message\": \"" + expectedMessage + "\"")) {
                content = result + ",{\"filename\":\"" + JsonUtil.escape(outputPath.toString()) + "\"}";
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            }
        } else if ("message/startswith".equalsIgnoreCase(route)
                || "message/starts-with".equalsIgnoreCase(route)) {
            if (!result.contains("\"Message\": \"" + expectedMessage)) {
                content = result + ",{\"filename\":\"" + JsonUtil.escape(outputPath.toString()) + "\"}";
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            }
        } else {
            int idx = result.indexOf("\"Message\": \"");
            if (idx < 0) {
                content = result + ",{\"filename\":\"" + JsonUtil.escape(outputPath.toString()) + "\"}";
                statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
            } else {
                String msgSlice = result.substring(idx + "\"Message\": \"".length());
                int endQuote = msgSlice.indexOf("\"");
                String messageValue = endQuote >= 0 ? msgSlice.substring(0, endQuote) : msgSlice;
                if (!messageValue.endsWith(expectedMessage)) {
                    content = result + ",{\"filename\":\"" + JsonUtil.escape(outputPath.toString()) + "\"}";
                    statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
                }
            }
        }

        respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
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
        if (route.startsWith("loganalytics/")) {
            route = route.substring("loganalytics/".length());
        }
        return route;
    }

    private static String safeName(String value) {
        return value == null ? "" : value.replace(" ", "_");
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

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }

    @FunctionalInterface
    public interface FileContentReader {
        String read(Path filePath) throws IOException;
    }
}

