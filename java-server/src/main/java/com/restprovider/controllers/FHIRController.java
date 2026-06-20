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
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class FHIRController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;
    private final HttpInvoker httpInvoker;
    private String cachedPat;

    public FHIRController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run, FHIRController::invokeHttp);
    }

    public FHIRController(PasscodeValidator passcodeValidator, CommandRunner commandRunner, HttpInvoker httpInvoker) {
        super("FHIR");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
        this.httpInvoker = httpInvoker;
        this.cachedPat = "";
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();

        if (!validatePassCode(request, response)) {
            return;
        }

        if (("GET".equalsIgnoreCase(method)
                || "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method))
                && "".equals(route)) {
            String objectType = HttpRequestUtil.headerValue(request, "objectType");
            String service = headerOrEnv(request, "fhirPaaSService", "RESTPROVIDER_FHIR_SERVICE", "");
            String token = retrievePat(service);
            if (token.isBlank()) {
                token = env("fhir_token");
            }

            String endpoint = "https://" + service + ".fhir.azurehealthcareapis.com/" + objectType;
            String result = httpInvoker.invoke(method.toUpperCase(), endpoint, token);
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

    private String retrievePat(String service) {
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

    private static String headerOrEnv(ClassicHttpRequest request, String header, String envName, String defaultValue) {
        String headerValue = HttpRequestUtil.headerValue(request, header);
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

    private static String invokeHttp(String method, String endpoint, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + token)
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
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

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }

    @FunctionalInterface
    public interface HttpInvoker {
        String invoke(String method, String endpoint, String bearerToken);
    }
}

