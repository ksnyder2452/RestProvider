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
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class SonarQubeController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final HttpExecutor httpExecutor;

    public SonarQubeController() {
        this(new EnvPasscodeValidator(), SonarQubeController::executeGraphQL);
    }

    public SonarQubeController(PasscodeValidator passcodeValidator, HttpExecutor httpExecutor) {
        super("SonarQube");
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

        if (("POST".equalsIgnoreCase(request.getMethod()) || "GET".equalsIgnoreCase(request.getMethod()))
                && ("".equals(route) || "graphql".equalsIgnoreCase(route))) {
            String serverName = readValue(request, query, "serverName", "host");
            String serverPort = defaultValue(readValue(request, query, "serverPort", "port"), "9000");
            String baseUrl = readValue(request, query, "sonarBaseUrl", "baseUrl");
            String graphQlQuery = readValue(request, query, "graphQLQuery", "query", "graphql");
            String apiToken = defaultValue(readValue(request, query, "sonarqubeApiToken", "apiToken", "token"), env("sonarqube_apitoken"));

            if (!require(response, "graphQLQuery", graphQlQuery)) {
                return;
            }
            if (baseUrl.isBlank() && serverName.isBlank()) {
                respondJson(response, HttpStatus.SC_BAD_REQUEST,
                        "{\"error\":\"Missing required parameter: serverName or sonarBaseUrl\"}");
                return;
            }
            if (!require(response, "sonarqubeApiToken", apiToken)) {
                return;
            }

            String endpoint = baseUrl.isBlank()
                    ? "https://" + serverName + ":" + serverPort + "/api/graphql"
                    : stripTrailingSlash(baseUrl) + "/api/graphql";

            String rowCount = httpExecutor.postGraphQL(endpoint, apiToken, graphQlQuery);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + JsonUtil.escape(rowCount) + "\"}");
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
        if (route.startsWith("sonarqube/")) {
            route = route.substring("sonarqube/".length());
        }
        if ("sonarqube".equalsIgnoreCase(route)) {
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

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String executeGraphQL(String endpoint, String token, String query) {
        try {
            String body = "{\"query\":\"" + JsonUtil.escape(query) + "\"}";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "SonarQube request failed: " + ex.getMessage();
        }
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface HttpExecutor {
        String postGraphQL(String endpoint, String apiToken, String query);
    }
}

