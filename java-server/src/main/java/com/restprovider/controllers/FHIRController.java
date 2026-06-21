package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
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

/**
 * Controller for the FHIR integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class FHIRController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;
    private final HttpInvoker httpInvoker;
    private String cachedPat;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public FHIRController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run, FHIRController::invokeHttp);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public FHIRController(PasscodeValidator passcodeValidator, CommandRunner commandRunner, HttpInvoker httpInvoker) {
        super("FHIR");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
        this.httpInvoker = httpInvoker;
        this.cachedPat = "";
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
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("GET".equalsIgnoreCase(method)
                || "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method))
                && ("".equals(route) || !route.isBlank())) {
            String objectType = readValue(request, query, "objectType", "resourceType", "resource");
            String resourceId = readValue(request, query, "resourceId", "id");

            String path = route;
            if (path.isBlank()) {
                if (objectType.isBlank()) {
                    respondJson(response, HttpStatus.SC_BAD_REQUEST,
                            "{\"error\":\"Missing required parameter: objectType or fhir route path\"}");
                    return;
                }
                path = objectType + (resourceId.isBlank() ? "" : "/" + resourceId);
            }

            String service = headerOrEnv(request, query, "fhirPaaSService", "RESTPROVIDER_FHIR_SERVICE", "");
            String baseUrl = readValue(request, query, "fhirBaseUrl", "baseUrl", "fhirUrl");
            if (baseUrl.isBlank() && service.isBlank()) {
                respondJson(response, HttpStatus.SC_BAD_REQUEST,
                        "{\"error\":\"Missing required parameter: fhirPaaSService or fhirBaseUrl\"}");
                return;
            }

            String token = readValue(request, query, "fhirToken", "token", "bearerToken");
            if (token.isBlank()) {
                token = retrievePat(service);
            }
            if (token.isBlank()) {
                token = env("fhir_token");
            }
            if (token.isBlank()) {
                respondJson(response, HttpStatus.SC_BAD_REQUEST,
                        "{\"error\":\"Missing FHIR access token (fhirToken or fhir_token env)\"}");
                return;
            }

            String payload = readValue(request, query, "payload", "body");
            String contentType = defaultValue(readValue(request, query, "contentType"), "application/fhir+json");

            String endpointBase = baseUrl.isBlank()
                    ? "https://" + service + ".fhir.azurehealthcareapis.com"
                    : stripTrailingSlash(baseUrl);
            String endpoint = endpointBase + "/" + path;

            String result = httpInvoker.invoke(method.toUpperCase(), endpoint, token, payload, contentType);
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

    private String retrievePat(String service) {
        if (service == null || service.isBlank()) {
            return "";
        }
        if (cachedPat != null && !cachedPat.isBlank()) {
            return cachedPat;
        }
        String cmd = "account get-access-token --resource=https://" + service
                + ".fhir.azurehealthcareapis.com --query accessToken --output tsv";
        cachedPat = commandRunner.run("az", cmd);
        return cachedPat == null ? "" : cachedPat.trim();
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("fhir/")) {
            route = route.substring("fhir/".length());
        }
        if ("fhir".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String headerOrEnv(ClassicHttpRequest request, Map<String, String> query,
                                      String header, String envName, String defaultValue) {
        String headerValue = readValue(request, query, header);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        String envValue = env(envName);
        return envValue.isBlank() ? defaultValue : envValue;
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

    private static String stripTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String invokeHttp(String method, String endpoint, String token, String payload, String contentType) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", contentType == null || contentType.isBlank()
                            ? "application/fhir+json"
                            : contentType);
            HttpRequest req;
            if (payload == null || payload.isBlank()) {
                req = builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
            } else {
                req = builder.method(method, HttpRequest.BodyPublishers.ofString(payload)).build();
            }
            // Outbound REST call to the target service endpoint.
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.body();
        } catch (Exception ex) {
            return "FHIR request failed: " + ex.getMessage();
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
    public interface CommandRunner {
        String run(String command, String args);
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface HttpInvoker {
        String invoke(String method, String endpoint, String bearerToken, String payload, String contentType);
    }
}



