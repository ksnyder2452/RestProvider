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
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class DataverseController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public DataverseController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public DataverseController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("Dataverse");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase();

        if (!validatePassCode(request, response)) {
            return;
        }

        if ("GET".equals(method) && "".equals(route)) {
            handleQuery(request, response);
            return;
        }
        if ("PUT".equals(method) && "ddl".equalsIgnoreCase(route)) {
            handleExecuteNonQuery(request, response);
            return;
        }
        if ("PUT".equals(method) && "".equals(route)) {
            handleExecuteNonQuery(request, response);
            return;
        }
        if ("DELETE".equals(method) && "".equals(route)) {
            handleExecuteNonQuery(request, response);
            return;
        }

        super.handle(request, response, subPath);
    }

    private void handleQuery(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));
        String sql = HttpRequestUtil.headerValue(request, "sql_statement");

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);
        String outputFile = headerOrDefault(request, "outputFile", testcaseName + "_dataverse_query_result.txt");
        Path outputPath = tempFolder.resolve(outputFile);
        Files.deleteIfExists(outputPath);

        String result = runDataverseSql(request, sql);
        if (isFailure(result)) {
            respondJson(response, HttpStatus.SC_BAD_REQUEST,
                    "{\"connectionFailure\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        long rowCount = Arrays.stream(result.split("\\R")).filter(s -> !s.isBlank()).count();
        respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rowCount + "\"}");
    }

    private void handleExecuteNonQuery(ClassicHttpRequest request, ClassicHttpResponse response) {
        String sql = HttpRequestUtil.headerValue(request, "sql_statement");
        String result = runDataverseSql(request, sql);
        if (isFailure(result)) {
            respondJson(response, HttpStatus.SC_BAD_REQUEST,
                    "{\"connectionFailure\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        String rowCount = extractRowsAffected(result);
        respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + JsonUtil.escape(rowCount) + "\"}");
    }

    private String runDataverseSql(ClassicHttpRequest request, String sql) {
        String env = HttpRequestUtil.headerValue(request, "dv_environment");
        String user = env("dv_user");
        String pwd = env("dv_password");
        String server = env + ".dynamics.com";
        String args = "-S \"" + server + "\" -U \"" + user + "\" -P \"" + pwd + "\" -Q \""
                + escapeQuotes(sql) + "\"";
        return commandRunner.run("sqlcmd", args);
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

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
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

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}

