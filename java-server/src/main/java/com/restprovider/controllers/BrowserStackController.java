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
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the BrowserStack integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class BrowserStackController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final HttpInvoker httpInvoker;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public BrowserStackController() {
        this(new EnvPasscodeValidator(), BrowserStackController::invoke);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public BrowserStackController(PasscodeValidator passcodeValidator, HttpInvoker httpInvoker) {
        super("BrowserStack");
        this.passcodeValidator = passcodeValidator;
        this.httpInvoker = httpInvoker;
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
        String method = request.getMethod().toUpperCase();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!"GET".equals(method)) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response, query)) {
            return;
        }

        String result;
        if ("session/list".equalsIgnoreCase(route)) {
            String buildId = readValue(request, query, "buildId", "build");
            if (!require(response, "buildId", buildId)) {
                return;
            }
            String limit = defaultValue(readValue(request, query, "numberSessions", "limit", "number"), "10");
            String endpoint = "https://api.browserstack.com/automate/builds/" + buildId + "/sessions.json?limit=" + limit;
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("build/list".equalsIgnoreCase(route)) {
            String limit = defaultValue(readValue(request, query, "numberBuilds", "limit", "number"), "10");
            String status = optionalQueryArg("status", readValue(request, query, "status"));
            String endpoint = "https://api.browserstack.com/automate/builds.json?limit=" + limit;
            if (!status.isBlank()) {
                endpoint = endpoint + status;
            }
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("session/details".equalsIgnoreCase(route)) {
            String sessionId = readValue(request, query, "sessionId", "session");
            if (!require(response, "sessionId", sessionId)) {
                return;
            }
            String endpoint = "https://api.browserstack.com/automate/sessions/" + sessionId + ".json";
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("build/details".equalsIgnoreCase(route)) {
            String buildId = readValue(request, query, "buildId", "build");
            if (!require(response, "buildId", buildId)) {
                return;
            }
            String endpoint = "https://api.browserstack.com/automate/builds/" + buildId + ".json";
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/build/list".equalsIgnoreCase(route)) {
            String limit = defaultValue(readValue(request, query, "numberBuilds", "limit", "number"), "10");
            String endpoint = "https://api-cloud.browserstack.com/app-automate/builds.json?limit=" + limit;
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/build/details".equalsIgnoreCase(route)) {
            String buildId = readValue(request, query, "buildId", "build");
            if (!require(response, "buildId", buildId)) {
                return;
            }
            String endpoint = "https://api-cloud.browserstack.com/app-automate/builds/" + buildId + "/sessions.json";
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/session/details".equalsIgnoreCase(route)) {
            String sessionId = readValue(request, query, "sessionId", "session");
            if (!require(response, "sessionId", sessionId)) {
                return;
            }
            String endpoint = "https://api-cloud.browserstack.com/app-automate/sessions/" + sessionId + ".json";
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("appium/session/list".equalsIgnoreCase(route)) {
            String buildId = readValue(request, query, "buildId", "build");
            if (!require(response, "buildId", buildId)) {
                return;
            }
            String endpoint = "https://api-cloud.browserstack.com/app-automate/builds/" + buildId + "/sessions.json";
            result = call(request, query, endpoint);
            writeOutput(request, query, "_browserstack_buildlist_result.txt", result);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private String call(ClassicHttpRequest request, Map<String, String> query, String endpoint) {
        String user = defaultValue(readValue(request, query, "browserstackUser", "username", "user"), env("browserstack_user"));
        String key = defaultValue(readValue(request, query, "browserstackAccessKey", "accessKey", "key"), env("browserstack_accesskey"));
        // Outbound REST call to the target service endpoint.
        return httpInvoker.call("GET", endpoint, user, key);
    }

    private void writeOutput(ClassicHttpRequest request, Map<String, String> query, String defaultSuffix, String result)
            throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "default");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "browserstack"));

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);

        String outputFile = readValue(request, query, "outputFile");
        if (outputFile == null || outputFile.isBlank()) {
            outputFile = testcaseName + defaultSuffix;
        }

        Path outputPath = tempFolder.resolve(outputFile);
        Files.writeString(outputPath, result == null ? "" : result, StandardCharsets.UTF_8);
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
        if (route.startsWith("browserstack/")) {
            route = route.substring("browserstack/".length());
        }
        if ("browserstack".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static boolean require(ClassicHttpResponse response, String key, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"" + JsonUtil.escape("Missing required parameter: " + key) + "\"}");
        return false;
    }

    private static String optionalQueryArg(String key, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "&" + key + "=" + value;
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
            // Outbound REST call to the target service endpoint.
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

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String user, String accessKey);
    }
}



