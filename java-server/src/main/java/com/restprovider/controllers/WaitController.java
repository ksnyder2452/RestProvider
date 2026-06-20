package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class WaitController extends BaseController {
    private final PasscodeValidator passcodeValidator;

    public WaitController() {
        this(new EnvPasscodeValidator());
    }

    public WaitController(PasscodeValidator passcodeValidator) {
        super("Wait");
        this.passcodeValidator = passcodeValidator;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (("GET".equals(method) || "POST".equals(method))
                && ("sleep".equalsIgnoreCase(route) || route.startsWith("sleep/"))) {
            int sleepFor = parseSleepSeconds(route, readValue(request, query, "sleepFor", "seconds"));
            try {
                Thread.sleep(Math.max(0, sleepFor) * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("Slept for " + sleepFor + " seconds", ContentType.TEXT_PLAIN));
            return;
        }

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "waitfor/invoke".equalsIgnoreCase(route)) {
            String expected = HttpRequestUtil.headerValue(request, "expectedString");
            String initialProcess = HttpRequestUtil.headerValue(request, "initialProcess");
            String finalProcess = HttpRequestUtil.headerValue(request, "finalProcess");
            String initialArgs = HttpRequestUtil.headerValue(request, "initialArguments");
            String finalArgs = HttpRequestUtil.headerValue(request, "finalArguments");
            int sleepMs = parseIntOrDefault(HttpRequestUtil.headerValue(request, "sleepInterval"), 30) * 1000;

            Thread t = new Thread(() -> {
                String status = "In Progress";
                while (!expected.contains(status)) {
                    status = ProcessUtil.run(initialProcess, initialArgs);
                    sleepQuietly(sleepMs);
                }
                ProcessUtil.run(finalProcess, finalArgs);
            });
            t.setDaemon(true);
            t.start();

            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"Test Case complete\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "waitforstatus".equalsIgnoreCase(route)) {
            startSimpleStatusThread(request, true);
            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"Test Case complete\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "waitfordifferentstatus".equalsIgnoreCase(route)) {
            startSimpleStatusThread(request, false);
            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"Test Case complete\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "wait/until/state/synchronous".equalsIgnoreCase(route)) {
            WaitResult result = executeWaitUntil(request, false);
            respondJson(response, result.statusCode, "{\"result\":\"" + JsonUtil.escape(result.result) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "wait/until/state/asynchronous".equalsIgnoreCase(route)) {
            Thread t = new Thread(() -> executeWaitUntil(request, true));
            t.setDaemon(true);
            t.start();
            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"Test Case complete\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private void startSimpleStatusThread(ClassicHttpRequest request, boolean expectContains) {
        String expected = HttpRequestUtil.headerValue(request, "expectedString");
        String process = HttpRequestUtil.headerValue(request, "process");
        String args = HttpRequestUtil.headerValue(request, "arguments");
        int sleepMs = parseIntOrDefault(HttpRequestUtil.headerValue(request, "sleepInterval"), 30) * 1000;

        Thread t = new Thread(() -> {
            String status = "In Progress";
            if (expectContains) {
                while (!expected.contains(status)) {
                    status = ProcessUtil.run(process, args);
                    sleepQuietly(sleepMs);
                }
            } else {
                while (expected.contains(status)) {
                    status = ProcessUtil.run(process, args);
                    sleepQuietly(sleepMs);
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private WaitResult executeWaitUntil(ClassicHttpRequest request, boolean asynchronousMode) {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String initialCommand = HttpRequestUtil.headerValue(request, asynchronousMode ? "initialCommand" : "command");
        String initialArgs = HttpRequestUtil.headerValue(request, asynchronousMode ? "initialArguments" : "arguments");
        String successCommand = HttpRequestUtil.headerValue(request, "finalSuccessfulCommand");
        String successArgs = HttpRequestUtil.headerValue(request, "finalSuccessfulArguments");
        String failedCommand = HttpRequestUtil.headerValue(request, "finalFailedCommand");
        String failedArgs = HttpRequestUtil.headerValue(request, "finalFailedArguments");

        int numberLoops = parseIntOrDefault(HttpRequestUtil.headerValue(request, "numberLoops"), 20) * 1000;
        int waitPeriod = parseIntOrDefault(HttpRequestUtil.headerValue(request, "waitPeriod"), 30) * 1000;
        String matchType = headerOrDefault(request, "matchType", "CONTAINS").toUpperCase();
        String matchString = HttpRequestUtil.headerValue(request, "matchString");
        boolean positive = "YES".equalsIgnoreCase(headerOrDefault(request, "isPositiveCheck", "YES"));

        String outputName = asynchronousMode ? "wait_until_asynchronous.txt" : "wait_until_synchronous.txt";
        Path out = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, outputName);
        try {
            Files.createDirectories(out.getParent());
            Files.deleteIfExists(out);
        } catch (IOException ignored) {
        }

        boolean found = false;
        String result = "String was not matched within the allotted time";
        for (int i = 0; i < numberLoops; i++) {
            sleepQuietly(waitPeriod);
            String current = ProcessUtil.run(initialCommand, initialArgs);
            appendLine(out, current);
            if (matches(current, matchString, matchType, positive)) {
                found = true;
                result = current;
                break;
            }
        }

        if (asynchronousMode) {
            String finalResult;
            if (found) {
                finalResult = ProcessUtil.run(successCommand, successArgs);
            } else {
                finalResult = ProcessUtil.run(failedCommand, failedArgs);
            }
            appendLine(out, finalResult);
            return new WaitResult("Test Case complete", HttpStatus.SC_OK);
        }

        return new WaitResult(result, found ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE);
    }

    private boolean matches(String current, String matchString, String matchType, boolean positive) {
        boolean raw;
        switch (matchType) {
            case "EQUALS":
                raw = current.equals(matchString);
                break;
            case "STARTSWITH":
                raw = current.startsWith(matchString);
                break;
            case "ENDSWITH":
                raw = current.endsWith(matchString);
                break;
            case "CONTAINS":
            default:
                raw = current.contains(matchString);
                break;
        }
        return positive ? raw : !raw;
    }

    private static void appendLine(Path file, String line) {
        try {
            Files.writeString(file, (line == null ? "" : line) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
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
        if (route.startsWith("wait/")) {
            route = route.substring("wait/".length());
        }
        if ("wait".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static int parseSleepSeconds(String route, String fallbackValue) {
        if (route.startsWith("sleep/")) {
            return parseIntOrDefault(route.substring("sleep/".length()), parseIntOrDefault(fallbackValue, 0));
        }
        return parseIntOrDefault(fallbackValue, 0);
    }

    private static String readValue(ClassicHttpRequest request, Map<String, String> query, String... names) {
        for (String name : names) {
            String headerValue = HttpRequestUtil.headerValue(request, name);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
            String queryValue = query.get(name);
            if (queryValue != null && !queryValue.isBlank()) {
                return queryValue;
            }
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

    private static class WaitResult {
        private final String result;
        private final int statusCode;

        private WaitResult(String result, int statusCode) {
            this.result = result;
            this.statusCode = statusCode;
        }
    }
}

