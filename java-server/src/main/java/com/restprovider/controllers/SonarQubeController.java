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

        if (!validatePassCode(request, response)) {
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            String serverName = HttpRequestUtil.headerValue(request, "serverName");
            String serverPort = HttpRequestUtil.headerValue(request, "serverPort");
            if (serverPort == null || serverPort.isBlank()) {
                serverPort = "9000";
            }
            String graphQlQuery = HttpRequestUtil.headerValue(request, "graphQLQuery");
            String apiToken = env("sonarqube_apitoken");
            String endpoint = "https://" + serverName + ":" + serverPort + "/api/graphql";

            String rowCount = httpExecutor.postGraphQL(endpoint, apiToken, graphQlQuery);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + JsonUtil.escape(rowCount) + "\"}");
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

