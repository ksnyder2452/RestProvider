package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
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

public class MSDController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public MSDController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public MSDController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("MSD");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if (("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))
                && ("".equals(route) || "query".equalsIgnoreCase(route))) {
            if (!validatePassCode(request, response, query)) {
                return;
            }

            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "msd");
            String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "query"));
            String server = defaultValue(readValue(request, query, "serverHostName", "server", "host"), env("serverHostName"));
            String user = defaultValue(readValue(request, query, "account", "user", "username"), env("azure_account"));
            String pwd = defaultValue(readValue(request, query, "password", "pwd"), env("azure_pwd"));
            String sql = readValue(request, query, "sql_statement", "sql", "query");

            if (!require(response, "sql_statement", sql)
                    || !require(response, "serverHostName", server)
                    || !require(response, "account", user)
                    || !require(response, "password", pwd)) {
                return;
            }

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);
            String defaultName = testcaseName + "_msd_query_result.txt";
            String outputFile = defaultValue(readValue(request, query, "outputFile"), defaultName);
            Path outputPath = tempFolder.resolve(outputFile);

            String args = "-S" + server + " -G -U " + user + " -P " + pwd
                    + " -Q " + quote(sql) + " -o " + quote(outputPath.toString());
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
        if (route.startsWith("msd/")) {
            route = route.substring("msd/".length());
        }
        if ("msd".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String safeName(String value) {
        return value == null ? "" : value.replace(" ", "_");
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
}

