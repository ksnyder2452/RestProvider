package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if ("GET".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            if (!validatePassCode(request, response)) {
                return;
            }

            String projectName = HttpRequestUtil.headerValue(request, "projectName");
            String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));
            String server = headerOrDefault(request, "serverHostName", env("serverHostName"));
            String user = headerOrDefault(request, "account", env("azure_account"));
            String pwd = headerOrDefault(request, "password", env("azure_pwd"));
            String sql = HttpRequestUtil.headerValue(request, "sql_statement");

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);
            String defaultName = testcaseName + "_msd_query_result.txt";
            String outputFile = headerOrDefault(request, "outputFile", defaultName);
            Path outputPath = tempFolder.resolve(outputFile);

            String args = "-S" + server + " -G -U " + user + " -P " + pwd
                    + " -Q " + quote(sql) + " -o " + quote(outputPath.toString());
            String result = commandRunner.run("sqlcmd", args);
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

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
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
}

