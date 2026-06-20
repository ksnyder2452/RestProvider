package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class SnowflakeController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final HttpExecutor httpExecutor;

    public SnowflakeController() {
        this(new EnvPasscodeValidator(), SnowflakeController::executeHttp);
    }

    public SnowflakeController(PasscodeValidator passcodeValidator, HttpExecutor httpExecutor) {
        super("Snowflake");
        this.passcodeValidator = passcodeValidator;
        this.httpExecutor = httpExecutor;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("GET".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && ("token".equalsIgnoreCase(route) || "oauth/token".equalsIgnoreCase(route))) {
            String endpoint = readValue(request, query, "az_token_endpoint", "tokenEndpoint", "endpoint");
            String scope = readValue(request, query, "az_scope", "scope");
            String azureUser = defaultValue(readValue(request, query, "az_user", "username", "user"), env("az_user"));
            String azurePassword = defaultValue(readValue(request, query, "az_password", "password", "pwd"), env("az_password"));
            String clientId = defaultValue(readValue(request, query, "oauth_client_id", "clientId"), env("oauth_client_id"));
            String clientSecret = defaultValue(readValue(request, query, "oauth_client_secret", "clientSecret"), env("oauth_client_secret"));

            if (!require(response, "az_token_endpoint", endpoint)
                    || !require(response, "az_scope", scope)
                    || !require(response, "oauth_client_id", clientId)
                    || !require(response, "oauth_client_secret", clientSecret)
                    || !require(response, "az_user", azureUser)
                    || !require(response, "az_password", azurePassword)) {
                return;
            }

            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret);
            form.put("username", azureUser);
            form.put("password", azurePassword);
            form.put("grant_type", "password");
            form.put("scope", scope);

            String result = httpExecutor.postForm(endpoint, form);
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
        if (route.startsWith("snowflake/")) {
            route = route.substring("snowflake/".length());
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

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean require(ClassicHttpResponse response, String field, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"Missing required parameter: " + JsonUtil.escape(field) + "\"}");
        return false;
    }

    private static String executeHttp(String endpoint, Map<String, String> form) {
        try {
            String body = encodeForm(form);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "Snowflake token request failed: " + ex.getMessage();
        }
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface HttpExecutor {
        String postForm(String endpoint, Map<String, String> form);
    }
}

