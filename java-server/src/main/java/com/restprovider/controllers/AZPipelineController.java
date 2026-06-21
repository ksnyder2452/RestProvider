package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the AZPipeline integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class AZPipelineController extends BaseController {
    private static final Pattern RESULT_PATTERN = Pattern.compile("\\\"result\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern STATUS_PATTERN = Pattern.compile("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public AZPipelineController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public AZPipelineController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("AZPipeline");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
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

        if ("GET".equalsIgnoreCase(method) && "pipeline/runs".equalsIgnoreCase(route)) {
            String organization = organizationUrl(readValue(request, query, "organization", "org"));
            String project = readValue(request, query, "project");
            String top = defaultValue(readValue(request, query, "top"), "10");
            if (!require(response, "organization", organization) || !require(response, "project", project)) {
                return;
            }

            String queryOrder = optionalArg("query-order", readValue(request, query, "queryOrder", "query-order"));
            String reason = optionalArg("reason", readValue(request, query, "reason"));
            String result = optionalArg("result", readValue(request, query, "result"));
            String status = optionalArg("status", readValue(request, query, "status"));
            String branch = optionalArg("branch", readValue(request, query, "branch"));
            String pipelineIds = optionalArg("pipeline-ids", readValue(request, query, "pipelineIds", "pipeline-ids"));
            String requestedFor = optionalArg("requested-for", readValue(request, query, "requestedFor", "requested-for"));
            String createdAfter = optionalArg("created-after", readValue(request, query, "createdAfter", "created-after"));
            String createdBefore = optionalArg("created-before", readValue(request, query, "createdBefore", "created-before"));

            String output = runAz("pipelines runs list --organization " + quoteArg(organization)
                    + " --project " + quoteArg(project)
                    + " --top " + quoteArg(top)
                    + queryOrder + reason + result + status + branch + pipelineIds
                    + requestedFor + createdAfter + createdBefore);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "pipeline/run".equalsIgnoreCase(route)) {
            String organization = organizationUrl(readValue(request, query, "organization", "org"));
            String project = readValue(request, query, "project");
            String runId = readValue(request, query, "runId", "id");
            if (!require(response, "organization", organization)
                    || !require(response, "project", project)
                    || !require(response, "runId", runId)) {
                return;
            }

            String output = runAz("pipelines runs show --id " + quoteArg(runId)
                    + " --organization " + quoteArg(organization)
                    + " --project " + quoteArg(project));
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "pipeline/run/waituntilcomplete".equalsIgnoreCase(route)) {
            String organization = organizationUrl(readValue(request, query, "organization", "org"));
            String project = readValue(request, query, "project");
            String runId = readValue(request, query, "runId", "id");
            int numberLoops = parseIntOrDefault(readValue(request, query, "numberLoops"), 240);
            int sleepMs = parseIntOrDefault(readValue(request, query, "sleepInterval", "sleepMs", "sleepSeconds"), 30_000);
            if (sleepMs > 0 && sleepMs < 1000) {
                sleepMs = sleepMs * 1000;
            }
            if (!require(response, "organization", organization)
                    || !require(response, "project", project)
                    || !require(response, "runId", runId)) {
                return;
            }

            String output = "";
            int loops = 0;
            while (loops < Math.max(1, numberLoops)) {
                output = runAz("pipelines runs show --id " + quoteArg(runId)
                        + " --organization " + quoteArg(organization)
                        + " --project " + quoteArg(project));
                String statusValue = extractField(STATUS_PATTERN, output).toLowerCase(Locale.ROOT);
                if (isTerminalStatus(statusValue)) {
                    break;
                }
                loops++;
                sleepQuietly(sleepMs);
            }

            String resultValue = extractField(RESULT_PATTERN, output);
            String statusValue = extractField(STATUS_PATTERN, output);
            respondJson(response, HttpStatus.SC_OK,
                    "{\"result\":\"" + JsonUtil.escape(resultValue.isBlank() ? output : resultValue)
                            + "\",\"status\":\"" + JsonUtil.escape(statusValue)
                            + "\",\"output\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "pipeline/run".equalsIgnoreCase(route)) {
            String organization = organizationUrl(readValue(request, query, "organization", "org"));
            String project = readValue(request, query, "project");
            String pipelineName = readValue(request, query, "pipelineName", "pipeline", "name");
            String pipelineId = readValue(request, query, "pipelineId", "id");
            if (!require(response, "organization", organization)
                    || !require(response, "project", project)) {
                return;
            }
            if (pipelineName.isBlank() && pipelineId.isBlank()) {
                respondBadRequest(response, "Missing required parameter: pipelineName or pipelineId");
                return;
            }

            String output = runAz("pipelines run"
                    + (pipelineName.isBlank() ? " --id " + quoteArg(pipelineId) : " --name " + quoteArg(pipelineName))
                    + " --organization " + quoteArg(organization)
                    + " --project " + quoteArg(project)
                    + optionalArg("branch", readValue(request, query, "branch"))
                    + optionalArg("commit-id", readValue(request, query, "commitId", "commit-id"))
                    + optionalArg("folder-path", readValue(request, query, "folderPath", "folder-path"))
                    + optionalArg("parameters", readValue(request, query, "parameters"))
                    + optionalArg("variables", readValue(request, query, "variables")));
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
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

    private String runAz(String args) {
        return commandRunner.run("az", args);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("az/")) {
            route = route.substring("az/".length());
        }
        if ("azpipeline".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String organizationUrl(String org) {
        String value = org == null ? "" : org.trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value.endsWith("/") ? value : value + "/";
        }
        return "https://dev.azure.com/" + value + "/";
    }

    private static String optionalArg(String key, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return " --" + key + " " + quoteArg(value);
    }

    private static String quoteArg(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\\\\\"") + "\"";
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean require(ClassicHttpResponse response, String key, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondBadRequest(response, "Missing required parameter: " + key);
        return false;
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

    private static boolean isTerminalStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return "completed".equals(normalized)
                || "canceled".equals(normalized)
                || "failed".equals(normalized)
                || "postponed".equals(normalized)
                || "notstarted".equals(normalized)
                || "abandoned".equals(normalized);
    }

    private static void respondBadRequest(ClassicHttpResponse response, String message) {
        respondJson(response, HttpStatus.SC_BAD_REQUEST, "{\"error\":\"" + JsonUtil.escape(message) + "\"}");
    }

    private static String extractField(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input == null ? "" : input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}



