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
import java.util.Base64;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class JenkinsController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final HttpInvoker httpInvoker;

    public JenkinsController() {
        this(new EnvPasscodeValidator(), JenkinsController::invoke);
    }

    public JenkinsController(PasscodeValidator passcodeValidator, HttpInvoker httpInvoker) {
        super("Jenkins");
        this.passcodeValidator = passcodeValidator;
        this.httpInvoker = httpInvoker;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if (!"".equals(route)
                || !("GET".equals(method) || "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response, query)) {
            return;
        }

        String subUri = readValue(request, query, "subURI", "subUri", "path", "jobPath");
        if (!require(response, "subURI", subUri)) {
            return;
        }

        String baseUrl = readValue(request, query, "jenkinsBaseUrl", "baseUrl", "serverUrl");
        String serverName = readValue(request, query, "serverName", "host");
        String serverPort = defaultValue(readValue(request, query, "serverPort", "port"), "8080");
        if (baseUrl.isBlank() && serverName.isBlank()) {
            respondJson(response, HttpStatus.SC_BAD_REQUEST,
                    "{\"error\":\"Missing required parameter: serverName or jenkinsBaseUrl\"}");
            return;
        }

        String user = defaultValue(readValue(request, query, "jenkinsUser", "username", "user"), env("jenkins_user"));
        String token = defaultValue(readValue(request, query, "jenkinsApiToken", "apiToken", "token"), env("jenkins_apitoken"));
        if (user.isBlank() || token.isBlank()) {
            respondJson(response, HttpStatus.SC_BAD_REQUEST,
                    "{\"error\":\"Missing Jenkins credentials (jenkinsUser/jenkinsApiToken or env vars)\"}");
            return;
        }

        String endpointBase = baseUrl.isBlank() ? "http://" + serverName + ":" + serverPort : stripTrailingSlash(baseUrl);
        String endpoint = endpointBase + "/" + stripLeadingSlash(subUri);

        String result = httpInvoker.call(method, endpoint, user, token);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
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
        if (route.startsWith("jenkins/")) {
            route = route.substring("jenkins/".length());
        }
        if ("jenkins".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
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

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String stripLeadingSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.startsWith("/")) {
            v = v.substring(1);
        }
        return v;
    }

    private static String invoke(String method, String endpoint, String user, String token) {
        try {
            String creds = Base64.getEncoder().encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Basic " + creds)
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception ex) {
            return "Jenkins request failed: " + ex.getMessage();
        }
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String user, String token);
    }
}

