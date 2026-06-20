package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

public class BrowserStackController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final HttpInvoker httpInvoker;

    public BrowserStackController() {
        this(new EnvPasscodeValidator(), BrowserStackController::invoke);
    }

    public BrowserStackController(PasscodeValidator passcodeValidator, HttpInvoker httpInvoker) {
        super("BrowserStack");
        this.passcodeValidator = passcodeValidator;
        this.httpInvoker = httpInvoker;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase();

        if (!"GET".equals(method)) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response)) {
            return;
        }

        String result;
        if ("session/list".equalsIgnoreCase(route)) {
            String buildId = HttpRequestUtil.headerValue(request, "buildId");
            String limit = headerOrDefault(request, "numberSessions", "10");
            String endpoint = "https://api.browserstack.com/automate/builds/" + buildId + "/sessions.json?limit=" + limit;
            result = call(request, endpoint);
            writeOutput(request, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("build/list".equalsIgnoreCase(route)) {
            String limit = headerOrDefault(request, "numberBuilds", "10");
            String endpoint = "https://api.browserstack.com/automate/builds.json?limit=" + limit;
            result = call(request, endpoint);
            writeOutput(request, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("session/details".equalsIgnoreCase(route)) {
            String sessionId = HttpRequestUtil.headerValue(request, "sessionId");
            String endpoint = "https://api.browserstack.com/automate/sessions/" + sessionId + ".json";
            result = call(request, endpoint);
            writeOutput(request, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/build/list".equalsIgnoreCase(route)) {
            String limit = headerOrDefault(request, "numberBuilds", "10");
            String endpoint = "https://api-cloud.browserstack.com/app-automate/builds.json?limit=" + limit;
            result = call(request, endpoint);
            writeOutput(request, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/build/details".equalsIgnoreCase(route)) {
            String buildId = HttpRequestUtil.headerValue(request, "buildId");
            String endpoint = "https://api-cloud.browserstack.com/app-automate/builds/" + buildId + "/sessions.json";
            result = call(request, endpoint);
            writeOutput(request, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/session/details".equalsIgnoreCase(route)) {
            String sessionId = HttpRequestUtil.headerValue(request, "sessionId");
            String endpoint = "https://api-cloud.browserstack.com/app-automate/sessions/" + sessionId + ".json";
            result = call(request, endpoint);
            writeOutput(request, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private String call(ClassicHttpRequest request, String endpoint) {
        String user = env("browserstack_user");
        String key = env("browserstack_accesskey");
        return httpInvoker.call("GET", endpoint, user, key);
    }

    private void writeOutput(ClassicHttpRequest request, String defaultSuffix, String result) throws IOException {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);

        String outputFile = HttpRequestUtil.headerValue(request, "outputFile");
        if (outputFile == null || outputFile.isBlank()) {
            outputFile = testcaseName + defaultSuffix;
        }

        Path outputPath = tempFolder.resolve(outputFile);
        Files.writeString(outputPath, result == null ? "" : result, StandardCharsets.UTF_8);
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
        if (route.startsWith("browserstack/")) {
            route = route.substring("browserstack/".length());
        }
        if ("browserstack".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String headerOrDefault(ClassicHttpRequest request, String headerName, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, headerName);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String safeName(String name) {
        return (name == null ? "" : name).replace(" ", "_");
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String invoke(String method, String endpoint, String user, String key) {
        try {
            String auth = java.util.Base64.getEncoder()
                    .encodeToString((user + ":" + key).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Basic " + auth)
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "BrowserStack request failed: " + ex.getMessage();
        }
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String user, String accessKey);
    }
}

