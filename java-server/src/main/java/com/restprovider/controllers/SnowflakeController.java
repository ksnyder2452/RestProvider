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

        if (!validatePassCode(request, response)) {
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "token".equalsIgnoreCase(route)) {
            String endpoint = HttpRequestUtil.headerValue(request, "az_token_endpoint");
            String scope = HttpRequestUtil.headerValue(request, "az_scope");
            String azureUser = env("az_user");
            String azurePassword = env("az_password");
            String clientId = env("oauth_client_id");
            String clientSecret = env("oauth_client_secret");

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
        if (route.startsWith("snowflake/")) {
            route = route.substring("snowflake/".length());
        }
        return route;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
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

