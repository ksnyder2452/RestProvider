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
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            super.handle(request, response, subPath);
            return;
        }

        if (!("message".equalsIgnoreCase(route)
                || "message/startswith".equalsIgnoreCase(route)
                || "message/endswith".equalsIgnoreCase(route))) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response)) {
            return;
        }

        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));
        String expectedMessage = HttpRequestUtil.headerValue(request, "expectedMessage");
        String runId = HttpRequestUtil.headerValue(request, "runId");
        String workspaceId = headerOrDefault(request, "analyticsWorkspaceId",
                env("RESTPROVIDER_LOGANALYTICS_WORKSPACE_ID"));

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);
        String outputFile = headerOrDefault(request, "outputFile", testcaseName + "_loganalytics.out");
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
        } else if ("message/startswith".equalsIgnoreCase(route)) {
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
        if (route.startsWith("loganalytics/")) {
            route = route.substring("loganalytics/".length());
        }
        return route;
    }

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String safeName(String value) {
        return value == null ? "" : value.replace(" ", "_");
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

