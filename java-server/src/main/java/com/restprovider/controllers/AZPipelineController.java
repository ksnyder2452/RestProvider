package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class AZPipelineController extends BaseController {
    private static final Pattern RESULT_PATTERN = Pattern.compile("\\\"result\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public AZPipelineController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public AZPipelineController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("AZPipeline");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
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

        if ("GET".equalsIgnoreCase(method) && "pipeline/runs".equalsIgnoreCase(route)) {
            String organization = organizationUrl(HttpRequestUtil.headerValue(request, "organization"));
            String project = HttpRequestUtil.headerValue(request, "project");
            String top = headerOrDefault(request, "top", "10");
            String queryOrder = optionalArg("query-order", HttpRequestUtil.headerValue(request, "queryOrder"));
            String reason = optionalArg("reason", HttpRequestUtil.headerValue(request, "reason"));
            String result = optionalArg("result", HttpRequestUtil.headerValue(request, "result"));
            String status = optionalArg("status", HttpRequestUtil.headerValue(request, "status"));
            String output = runAz("pipelines runs list --organization " + organization + " --project " + project
                    + " --top " + top + queryOrder + reason + result + status);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "pipeline/run".equalsIgnoreCase(route)) {
            String organization = organizationUrl(HttpRequestUtil.headerValue(request, "organization"));
            String project = HttpRequestUtil.headerValue(request, "project");
            String runId = HttpRequestUtil.headerValue(request, "runId");
            String output = runAz("pipelines runs show --id " + runId + " --organization " + organization
                    + " --project " + project);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "pipeline/run/waituntilcomplete".equalsIgnoreCase(route)) {
            String organization = organizationUrl(HttpRequestUtil.headerValue(request, "organization"));
            String project = HttpRequestUtil.headerValue(request, "project");
            String runId = HttpRequestUtil.headerValue(request, "runId");
            int numberLoops = parseIntOrDefault(HttpRequestUtil.headerValue(request, "numberLoops"), 1);

            String output = "";
            int loops = 0;
            while (loops < Math.max(1, numberLoops)) {
                output = runAz("pipelines runs show --id " + runId + " --organization " + organization
                        + " --project " + project);
                String lower = output.toLowerCase();
                if (lower.contains("\"status\": \"completed\"")
                        || lower.contains("\"status\":\"completed\"")
                        || lower.contains("\"status\": \"postponed\"")
                        || lower.contains("\"status\":\"postponed\"")
                        || lower.contains("\"status\": \"notstarted\"")
                        || lower.contains("\"status\":\"notstarted\"")) {
                    break;
                }
                loops++;
                sleepQuietly(30_000);
            }

            String resultValue = extractField(RESULT_PATTERN, output);
            respondJson(response, HttpStatus.SC_OK,
                    "{\"result\":\"" + JsonUtil.escape(resultValue.isBlank() ? output : resultValue) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "pipeline/run".equalsIgnoreCase(route)) {
            String organization = organizationUrl(HttpRequestUtil.headerValue(request, "organization"));
            String project = HttpRequestUtil.headerValue(request, "project");
            String pipelineName = HttpRequestUtil.headerValue(request, "pipelineName");
            String output = runAz("pipelines run --name " + pipelineName + " --organization " + organization
                    + " --project " + project);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
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
        return "https://dev.azure.com/" + org + "/";
    }

    private static String optionalArg(String key, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return " --" + key + " " + value;
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

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}

