package com.restprovider.controllers;

import com.restprovider.core.BaseController;
import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the Grafana integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class GrafanaController extends BaseController {
    private static final String GRAFANA_ROOT = "https://grafana.com";

    private final PasscodeValidator passcodeValidator;
    private final TokenProvider tokenProvider;
    private final HttpInvoker httpInvoker;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public GrafanaController() {
        this(new EnvPasscodeValidator(), GrafanaController::resolveApiToken, GrafanaController::invokeGrafana);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public GrafanaController(PasscodeValidator passcodeValidator,
                             TokenProvider tokenProvider,
                             HttpInvoker httpInvoker) {
        super("Grafana");
        this.passcodeValidator = passcodeValidator;
        this.tokenProvider = tokenProvider;
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

        if (!("".equals(route) || "request".equalsIgnoreCase(route))) {
            super.handle(request, response, subPath);
            return;
        }

        if (!("GET".equals(method) || "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            super.handle(request, response, subPath);
            return;
        }

        String passCode = readValue(request, query, "passCode", "passcode");
        if (!passcodeValidator.isValid(passCode)) {
            logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
            respondText(response, HttpStatus.SC_OK, "Passcode failure");
            return;
        }

        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "grafana");
        String grafanaRequest = readValue(request, query, "grafanaRequest", "request", "path");
        String apiVersion = defaultValue(readValue(request, query, "apiVersion", "version"), "1");
        String baseUrl = defaultValue(readValue(request, query, "grafanaBaseUrl", "baseUrl"), GRAFANA_ROOT);

        if (!require(response, "grafanaRequest", grafanaRequest)) {
            return;
        }

        String token = defaultValue(readValue(request, query, "grafanaApiToken", "token", "bearerToken"), tokenProvider.getToken());
        if (!require(response, "grafanaApiToken", token)) {
            return;
        }

        String endpoint = stripTrailingSlash(baseUrl) + "/api/v" + apiVersion + "/" + stripSlashes(grafanaRequest);

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);
        String outputFile = defaultValue(readValue(request, query, "outputFile"), "grafana_get_response.txt");
        Path outputPath = tempFolder.resolve(outputFile);
        if (Files.exists(outputPath)) {
            Files.delete(outputPath);
        }

        // Outbound REST call to the target service endpoint.
        String responseBody = httpInvoker.call(method, endpoint, token);
        Files.writeString(outputPath, responseBody, StandardCharsets.UTF_8);
        respondText(response, HttpStatus.SC_OK, "Grafana Response written to " + outputPath);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("grafana/")) {
            route = route.substring("grafana/".length());
        }
        if ("grafana".equalsIgnoreCase(route)) {
            return "";
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
        respondText(response, HttpStatus.SC_BAD_REQUEST, "Missing required parameter: " + field);
        return false;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String stripSlashes(String value) {
        String v = value == null ? "" : value.trim();
        while (v.startsWith("/")) {
            v = v.substring(1);
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String resolveApiToken() {
        return env("grafanaApiToken");
    }

    private static String invokeGrafana(String method, String endpoint, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + token)
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            // Outbound REST call to the target service endpoint.
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "Grafana request failed: " + ex.getMessage();
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static void respondText(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface TokenProvider {
        String getToken();
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String bearerToken);
    }
}



