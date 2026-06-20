package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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

public class PowerBIController extends BaseController {
    private static final String POWER_BI_ROOT = "https://api.powerbi.com";

    private final PasscodeValidator passcodeValidator;
    private final TokenProvider tokenProvider;
    private final HttpInvoker httpInvoker;

    public PowerBIController() {
        this(new EnvPasscodeValidator(), PowerBIController::resolveAccessToken, PowerBIController::invokePowerBi);
    }

    public PowerBIController(PasscodeValidator passcodeValidator,
                             TokenProvider tokenProvider,
                             HttpInvoker httpInvoker) {
        super("PowerBI");
        this.passcodeValidator = passcodeValidator;
        this.tokenProvider = tokenProvider;
        this.httpInvoker = httpInvoker;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase();

        if (!"".equals(route)) {
            super.handle(request, response, subPath);
            return;
        }

        if (!("GET".equals(method) || "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            super.handle(request, response, subPath);
            return;
        }

        String passCode = HttpRequestUtil.headerValue(request, "passCode");
        if (!passcodeValidator.isValid(passCode)) {
            logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
            respondText(response, HttpStatus.SC_OK, "Passcode failure");
            return;
        }

        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String powerBIRequest = HttpRequestUtil.headerValue(request, "powerBIRequest");
        String organization = HttpRequestUtil.headerValue(request, "organization");
        String apiVersion = HttpRequestUtil.headerValue(request, "apiVersion");

        String token = tokenProvider.getToken();
        String endpoint = POWER_BI_ROOT + "/v" + apiVersion + "/" + organization + "/" + powerBIRequest + "/";

        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        Files.createDirectories(tempFolder);
        Path outputPath = tempFolder.resolve("powerBI_get_response.txt");
        if (Files.exists(outputPath)) {
            Files.delete(outputPath);
        }

        String responseBody = httpInvoker.call(method, endpoint, token);
        Files.writeString(outputPath, responseBody, StandardCharsets.UTF_8);
        respondText(response, HttpStatus.SC_OK, "PowerBI Response written to " + outputPath);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("powerbi/")) {
            route = route.substring("powerbi/".length());
        }
        if ("powerbi".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String resolveAccessToken() {
        String predefined = env("powerBIAccessToken");
        if (!predefined.isBlank()) {
            return predefined;
        }

        String clientId = env("powerBIClientId");
        String clientSecret = env("powerBIClientSecret");
        String tenantId = env("powerBITenantId");
        if (clientId.isBlank() || clientSecret.isBlank() || tenantId.isBlank()) {
            return "";
        }

        try {
            String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
            String form = "client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&scope=" + enc("https://analysis.windows.net/powerbi/api/.default")
                    + "&grant_type=client_credentials";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return extractAccessToken(res.body());
        } catch (Exception ex) {
            return "";
        }
    }

    private static String invokePowerBi(String method, String endpoint, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + token)
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "PowerBI request failed: " + ex.getMessage();
        }
    }

    private static String extractAccessToken(String json) {
        String key = "\"access_token\":\"";
        int idx = json == null ? -1 : json.indexOf(key);
        if (idx < 0) {
            return "";
        }
        String rest = json.substring(idx + key.length());
        int end = rest.indexOf('"');
        return end < 0 ? "" : rest.substring(0, end);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void respondText(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    }

    @FunctionalInterface
    public interface TokenProvider {
        String getToken();
    }

    @FunctionalInterface
    public interface HttpInvoker {
        String call(String method, String endpoint, String bearerToken);
    }
}

