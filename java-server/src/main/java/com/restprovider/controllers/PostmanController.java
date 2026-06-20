package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class PostmanController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public PostmanController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public PostmanController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("Postman");
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

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if ("GET".equalsIgnoreCase(method) && ("status".equalsIgnoreCase(route) || "login/status".equalsIgnoreCase(route))) {
            String postmanUser = readValue(request, query, "postmanUser", "user", "username");
            String login = postmanLogin(request, query);
            int statusCode = (login.contains("Logged in using api key of user: " + postmanUser)
                    || login.contains("Logged in successfully"))
                    ? HttpStatus.SC_OK
                    : HttpStatus.SC_NOT_ACCEPTABLE;
            respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("run/id".equalsIgnoreCase(route) || "collection/run/id".equalsIgnoreCase(route))) {
            String collectionId = readValue(request, query, "collectionId", "id");
            if (!require(response, "collectionId", collectionId)) {
                return;
            }
            String login = postmanLogin(request, query);
            if (!login.contains("Logged in successfully")
                    && !login.contains("Logged in using api key")) {
                respondJson(response, HttpStatus.SC_NOT_ACCEPTABLE,
                        "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
                return;
            }

            Thread thread = new Thread(() -> commandRunner.run("postman", "collection run " + collectionId));
            thread.setDaemon(true);
            thread.start();

            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("run/file".equalsIgnoreCase(route) || "collection/run/file".equalsIgnoreCase(route))) {
            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "postman");
            String collectionPath = readValue(request, query, "collectionPath", "file", "path");
            if (!require(response, "collectionPath", collectionPath)) {
                return;
            }
            String login = postmanLogin(request, query);
            if (!login.contains("Logged in successfully")
                    && !login.contains("Logged in using api key")) {
                respondJson(response, HttpStatus.SC_NOT_ACCEPTABLE,
                        "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
                return;
            }

            String rootDir = System.getProperty("user.dir");
            String fullPath = Path.of(rootDir, "data_files", "temp", projectName, collectionPath).toString();
            Thread thread = new Thread(() -> commandRunner.run("postman", "collection run " + fullPath));
            thread.setDaemon(true);
            thread.start();

            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
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

    private String postmanLogin(ClassicHttpRequest request, Map<String, String> query) {
        String apiKey = defaultValue(readValue(request, query, "postmanApiKey", "apiKey", "key"), System.getenv("postman_key"));
        return commandRunner.run("postman", "login --with-api-key " + (apiKey == null ? "" : apiKey));
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("postman/")) {
            route = route.substring("postman/".length());
        }
        return route;
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
                "{\"error\":\"Missing required parameter: " + JsonUtil.escape(field) + "\"}");
        return false;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
