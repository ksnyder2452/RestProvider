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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import com.restprovider.core.BaseController;

public class OSController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public OSController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public OSController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("OS");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if ("GET".equals(method) && "year".equalsIgnoreCase(route)) {
            String format = readValue(request, query, "format", "dateFormat");
            if (format == null || format.isBlank()) {
                format = "yyyy";
            }
            String year = LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
            respondJson(response, HttpStatus.SC_OK, "{\"year\":\"" + JsonUtil.escape(year) + "\"}");
            return;
        }

        if ("GET".equals(method) && "month".equalsIgnoreCase(route)) {
            String format = readValue(request, query, "format", "dateFormat");
            if (format == null || format.isBlank()) {
                format = "MM";
            }
            String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
            respondJson(response, HttpStatus.SC_OK, "{\"month\":\"" + JsonUtil.escape(month) + "\"}");
            return;
        }

        if ("GET".equals(method) && "time".equalsIgnoreCase(route)) {
            String timestamp = LocalDateTime.now().toString();
            respondJson(response, HttpStatus.SC_OK, "{\"Timestamp\":\"" + JsonUtil.escape(timestamp) + "\"}");
            return;
        }

        if ("GET".equals(method) && "ip".equalsIgnoreCase(route)) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://ipinfo.io/ip")).GET().build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                respondJson(response, HttpStatus.SC_OK,
                        "{\"Content\":\"" + JsonUtil.escape(res.body().trim()) + "\"}");
            } catch (Exception ex) {
                respondJson(response, HttpStatus.SC_OK,
                        "{\"Content\":\"" + JsonUtil.escape("Unavailable") + "\"}");
            }
            return;
        }

        if ("POST".equals(method) && ("folder".equalsIgnoreCase(route)
                || "folder/create".equalsIgnoreCase(route))) {
            String project = readValue(request, query, "projectName", "project");
            String folderName = readValue(request, query, "folderName", "folder", "path");
            String recreate = readValue(request, query, "recreate", "recreateIfExists");
            if (!require(response, "folderName", folderName)) {
                return;
            }
            Path target = tempProjectRoot(project).resolve(folderName).normalize();
            if ("Yes".equalsIgnoreCase(recreate)) {
                deleteRecursively(target);
            }
            Files.createDirectories(target);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(folderName + " has been created", ContentType.TEXT_PLAIN));
            return;
        }

        if ("GET".equals(method) && ("folder".equalsIgnoreCase(route)
                || "folder/root".equalsIgnoreCase(route))) {
            String rootDir = System.getProperty("user.dir");
            respondJson(response, HttpStatus.SC_OK, "{\"rootDir\":\"" + JsonUtil.escape(rootDir) + "\"}");
            return;
        }

        if ("DELETE".equals(method) && ("folder/contents".equalsIgnoreCase(route)
                || "folder/clean".equalsIgnoreCase(route))) {
            String project = readValue(request, query, "projectName", "project");
            String folderName = readValue(request, query, "folderName", "folder", "path");
            if (!require(response, "folderName", folderName)) {
                return;
            }
            Path target = tempProjectRoot(project).resolve(folderName).normalize();
            if (Files.exists(target) && Files.isDirectory(target)) {
                try (var walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder())
                            .filter(p -> !p.equals(target))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(folderName + " is clean", ContentType.TEXT_PLAIN));
            return;
        }

        if ("GET".equals(method) && ("variable".equalsIgnoreCase(route)
                || "env/variable".equalsIgnoreCase(route))) {
            String varName = readValue(request, query, "varName", "name", "variableName");
            if (!require(response, "varName", varName)) {
                return;
            }
            String envValue;
            if ("git_token".equals(varName) || "AZURE_DEVOPS_EXT_PAT".equals(varName)) {
                envValue = "NotFound";
            } else {
                envValue = System.getenv(varName);
                if (envValue == null || envValue.isBlank()) {
                    envValue = "NotFound";
                }
            }
            respondJson(response, HttpStatus.SC_OK,
                    "{\"variableValue\":\"" + JsonUtil.escape(envValue) + "\"}");
            return;
        }

        if ("POST".equals(method) && ("variable".equalsIgnoreCase(route)
                || "env/variable".equalsIgnoreCase(route))) {
            String varName = readValue(request, query, "varName", "name", "variableName");
            String varValue = readValue(request, query, "varValue", "value", "variableValue");
            if (!require(response, "varName", varName)) {
                return;
            }
            setEnvVarForCurrentProcess(varName, varValue);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(varName + "was set", ContentType.TEXT_PLAIN));
            return;
        }

        if ("GET".equals(method) && "crontab".equalsIgnoreCase(route)) {
            String content = "";
            try {
                Process process = new ProcessBuilder("crontab", "-l")
                        .redirectErrorStream(true)
                        .start();
                content = new String(process.getInputStream().readAllBytes());
                process.waitFor();
            } catch (Exception ex) {
                content = "crontab unavailable on this host";
            }
            respondJson(response, HttpStatus.SC_OK,
                    "{\"Content\":\"" + JsonUtil.escape(content.trim()) + "\"}");
            return;
        }

        if ("POST".equals(method) && ("insightlink/session/schedule".equalsIgnoreCase(route)
                || "session/schedule".equalsIgnoreCase(route))) {
            handleScheduleSession(request, response, query);
            return;
        }

        super.handle(request, response, subPath);
    }

    private void handleScheduleSession(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String passCode = readValue(request, query, "passCode", "passcode");
        if (!passcodeValidator.isValid(passCode)) {
            logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
            respondJson(response, HttpStatus.SC_UNAUTHORIZED, "{\"passCodeResult\":\"Passcode failure\"}");
            return;
        }

        String projectName = readValue(request, query, "projectName", "project");
        String sessionName = readValue(request, query, "sessionName", "session");
        String schedule = readValue(request, query, "schedule", "interval");
        int maxRuns = parseIntOrDefault(readValue(request, query, "maxRuns", "runs"), 1);
        if (!require(response, "projectName", projectName) || !require(response, "sessionName", sessionName)) {
            return;
        }

        String group = envOr("rb_group", envOr("rbGroup", ""));
        String account = envOr("rb_account", envOr("rbAccount", ""));

        Path archivePath = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, "Archives", group);
        String scriptName = resolveScriptName(archivePath, sessionName);
        if (scriptName.isBlank()) {
            respondJson(response, HttpStatus.SC_NOT_ACCEPTABLE,
                    "{\"Content\":\"" + JsonUtil.escape("No session script found for " + sessionName) + "\"}");
            return;
        }

        int intervalSeconds = parseScheduleToSeconds(schedule);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(new Runnable() {
            private int runCount;

            @Override
            public void run() {
                if (runCount >= Math.max(1, maxRuns)) {
                    executor.shutdown();
                    return;
                }
                String args = archivePath.resolve(scriptName) + " \"" + passCode + "\" " + account + " YES";
                commandRunner.run("sh", args);
                runCount++;
            }
        }, 0, Math.max(1, intervalSeconds), TimeUnit.SECONDS);

        String content = "Schedule for " + sessionName + " has been submitted";
        respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
    }

    private static Path tempProjectRoot(String project) {
        return Paths.get(System.getProperty("user.dir"), "data_files", "temp", project == null ? "" : project);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("os/")) {
            route = route.substring("os/".length());
        }
        if ("os".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
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

    private static boolean require(ClassicHttpResponse response, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"Missing required field: " + JsonUtil.escape(fieldName) + "\"}");
        return false;
    }

    private static String resolveScriptName(Path archivePath, String sessionName) throws IOException {
        if (!Files.exists(archivePath) || !Files.isDirectory(archivePath)) {
            return "";
        }
        try (var files = Files.list(archivePath)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".sh"))
                    .filter(name -> name.contains(sessionName))
                    .findFirst()
                    .orElse("");
        }
    }

    private static int parseScheduleToSeconds(String schedule) {
        if (schedule == null || schedule.isBlank()) {
            return 60;
        }
        String trimmed = schedule.trim();
        if (trimmed.matches("\\d+")) {
            return Math.max(1, Integer.parseInt(trimmed));
        }

        // Accept simple cron minute expressions like */5 * * * *
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 1 && parts[0].startsWith("*/")) {
            try {
                int minutes = Integer.parseInt(parts[0].substring(2));
                return Math.max(1, minutes * 60);
            } catch (Exception ignored) {
            }
        }
        if ("*".equals(parts[0])) {
            return 60;
        }
        return 60;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String envOr(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static void deleteRecursively(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void setEnvVarForCurrentProcess(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            var cl = env.getClass();
            var field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writable = (Map<String, String>) field.get(env);
            writable.put(key, value);
        } catch (Exception ignored) {
        }
    }

    private static void respondJson(ClassicHttpResponse response, int code, String json) {
        response.setCode(code);
        response.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}

