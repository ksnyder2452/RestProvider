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
        if (!"".equals(route)
                || !("GET".equals(method) || "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response)) {
            return;
        }

        String subUri = HttpRequestUtil.headerValue(request, "subURI");
        String serverName = HttpRequestUtil.headerValue(request, "serverName");
        String serverPort = headerOrDefault(request, "serverPort", "8080");
        String user = env("jenkins_user");
        String token = env("jenkins_apitoken");
        String endpoint = "http://" + serverName + ":" + serverPort + "/" + subUri;

        String result = httpInvoker.call(method, endpoint, user, token);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
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
        if (route.startsWith("jenkins/")) {
            route = route.substring("jenkins/".length());
        }
        if ("jenkins".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
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

