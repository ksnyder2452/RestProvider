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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class MiscController extends BaseController {
    private static final DateTimeFormatter RANDOM_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String SAFE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()`~-_=+[{]}|;:',<.>/?";

    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;
    private final Random random;
    private final Map<String, String> variableData;

    public MiscController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run, new Random(), new ConcurrentHashMap<>());
    }

    public MiscController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        this(passcodeValidator, commandRunner, new Random(), new ConcurrentHashMap<>());
    }

    public MiscController(PasscodeValidator passcodeValidator,
                          CommandRunner commandRunner,
                          Random random,
                          Map<String, String> variableData) {
        super("Misc");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
        this.random = random;
        this.variableData = variableData;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod().toUpperCase();

        if ("GET".equals(method) && "check/vpn".equalsIgnoreCase(route)) {
            handleCheckVpn(request, response);
            return;
        }
        if ("GET".equals(method) && "heartbeat".equalsIgnoreCase(route)) {
            handleHeartbeat(request, response);
            return;
        }
        if ("GET".equals(method) && "time/diff".equalsIgnoreCase(route)) {
            handleTimeDiff(request, response);
            return;
        }
        if ("POST".equals(method) && "process/run".equalsIgnoreCase(route)) {
            handleProcessRun(request, response);
            return;
        }
        if ("POST".equals(method) && "server/start".equalsIgnoreCase(route)) {
            handleServerStart(request, response);
            return;
        }
        if ("POST".equals(method) && "server/stop".equalsIgnoreCase(route)) {
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("Server stop requested", ContentType.TEXT_PLAIN));
            return;
        }
        if ("GET".equals(method) && "random/integer".equalsIgnoreCase(route)) {
            handleRandomInteger(request, response);
            return;
        }
        if ("GET".equals(method) && "random/double".equalsIgnoreCase(route)) {
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(String.valueOf(random.nextDouble()), ContentType.TEXT_PLAIN));
            return;
        }
        if ("GET".equals(method) && "random/date".equalsIgnoreCase(route)) {
            handleRandomDate(request, response);
            return;
        }
        if ("GET".equals(method) && "random/string".equalsIgnoreCase(route)) {
            handleRandomString(request, response);
            return;
        }

        if ("GET".equals(method) && "file/property".equalsIgnoreCase(route)) {
            if (!validatePassCode(request, response)) {
                return;
            }
            handleFileProperty(request, response);
            return;
        }
        if ("GET".equals(method) && "variables".equalsIgnoreCase(route)) {
            if (!validatePassCode(request, response)) {
                return;
            }
            loadVariables(false);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"Variables have been read\"}");
            return;
        }
        if ("GET".equals(method) && "data/variable".equalsIgnoreCase(route)) {
            if (!validatePassCode(request, response)) {
                return;
            }
            handleDataVariable(request, response);
            return;
        }
        if ("GET".equals(method) && "account/names".equalsIgnoreCase(route)) {
            if (!validatePassCode(request, response)) {
                return;
            }
            handleAccountNames(response);
            return;
        }
        if ("PUT".equals(method) && "credential".equalsIgnoreCase(route)) {
            if (!validatePassCode(request, response)) {
                return;
            }
            handleCredentialUpdate(request, response);
            return;
        }

        super.handle(request, response, subPath);
    }

    private void handleCheckVpn(ClassicHttpRequest request, ClassicHttpResponse response) {
        String expectedNetwork = HttpRequestUtil.headerValue(request, "expectedNetwork");
        String ip = commandRunner.run("curl", "--silent https://ipinfo.io/ip").trim();
        int statusCode = ip.startsWith(expectedNetwork + ".") ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE;
        respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(ip) + "\"}");
    }

    private void handleHeartbeat(ClassicHttpRequest request, ClassicHttpResponse response) {
        String hostName = HttpRequestUtil.headerValue(request, "hostName");
        String output = commandRunner.run("ping", hostName + " -c 1");
        int statusCode = output.contains("0.0% packet loss") || output.toLowerCase().contains("received = 1")
                ? HttpStatus.SC_OK
                : HttpStatus.SC_SERVICE_UNAVAILABLE;
        respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(output) + "\"}");
    }

    private void handleTimeDiff(ClassicHttpRequest request, ClassicHttpResponse response) {
        String beginTime = HttpRequestUtil.headerValue(request, "beginTime").trim();
        String endTime = HttpRequestUtil.headerValue(request, "endTime").trim();
        LocalDateTime begin = LocalDateTime.parse(beginTime);
        LocalDateTime end = LocalDateTime.parse(endTime);
        String span = Duration.between(begin, end).toString();
        respondJson(response, HttpStatus.SC_OK, "{\"Timespan\":\"" + JsonUtil.escape(span) + "\"}");
    }

    private void handleProcessRun(ClassicHttpRequest request, ClassicHttpResponse response) {
        String processName = HttpRequestUtil.headerValue(request, "processName");
        String processArgs = HttpRequestUtil.headerValue(request, "processArguments");
        String content = commandRunner.run(processName, processArgs);
        respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
    }

    private void handleServerStart(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        Files.createDirectories(Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName));
        Files.createDirectories(Path.of(System.getProperty("user.dir"), "data_files", "failures", projectName));
        loadVariables(false);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"Server is ready\"}");
    }

    private void handleRandomInteger(ClassicHttpRequest request, ClassicHttpResponse response) {
        int min = parseIntOrDefault(HttpRequestUtil.headerValue(request, "minVal"), 0);
        int max = parseIntOrDefault(HttpRequestUtil.headerValue(request, "maxVal"), Integer.MAX_VALUE);
        int value = min >= max ? min : random.nextInt(max - min) + min;
        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new StringEntity(String.valueOf(value), ContentType.TEXT_PLAIN));
    }

    private void handleRandomDate(ClassicHttpRequest request, ClassicHttpResponse response) {
        String minDate = HttpRequestUtil.headerValue(request, "minDate");
        String maxDate = HttpRequestUtil.headerValue(request, "maxDate");
        LocalDate start = LocalDate.parse(minDate, RANDOM_DATE_FORMAT);
        LocalDate end = LocalDate.parse(maxDate, RANDOM_DATE_FORMAT);
        int days = (int) Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays();
        LocalDate randomDate = days <= 0 ? start : start.plusDays(random.nextInt(days));
        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new StringEntity(randomDate.toString(), ContentType.TEXT_PLAIN));
    }

    private void handleRandomString(ClassicHttpRequest request, ClassicHttpResponse response) {
        int length = parseIntOrDefault(HttpRequestUtil.headerValue(request, "length"), 0);
        boolean allowSpecial = "YES".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "allowSpecialChars"));
        String chars = allowSpecial ? SAFE_CHARS + SPECIAL_CHARS : SAFE_CHARS;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(0, length); i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new StringEntity(sb.toString(), ContentType.TEXT_PLAIN));
    }

    private void handleFileProperty(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String relativePath = HttpRequestUtil.headerValue(request, "filePath");
        String fileName = HttpRequestUtil.headerValue(request, "fileName");
        String propertyName = HttpRequestUtil.headerValue(request, "propertyName");

        String cleanPath = relativePath.endsWith("/") ? relativePath : relativePath + "/";
        Path file = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, cleanPath, fileName);
        Map<String, String> data = parseProperties(file);
        String result = data.getOrDefault(propertyName, "");
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleDataVariable(ClassicHttpRequest request, ClassicHttpResponse response) {
        String variableName = HttpRequestUtil.headerValue(request, "variableName");
        if (variableName.contains("rb_credentials")
                || variableName.contains("cosmosConnectString")
                || variableName.contains("oracleConnectString")
                || variableName.contains("sqlServerConnectString")) {
            respondJson(response, HttpStatus.SC_FORBIDDEN, "{\"forbiddenInfo\":\"Invalid data\"}");
            return;
        }

        loadVariables(false);
        String value = variableData.getOrDefault(variableName, "");
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(value) + "\"}");
    }

    private void handleAccountNames(ClassicHttpResponse response) {
        loadVariables(false);
        String accounts = variableData.getOrDefault("rb_accounts",
                variableData.getOrDefault("rbAccounts", System.getenv().getOrDefault("rb_accounts", "")));
        List<String> names = splitCsv(accounts);
        String joined = String.join(",", names);
        respondJson(response, HttpStatus.SC_OK, "{\"rbAccounts\":\"" + JsonUtil.escape(joined) + "\"}");
    }

    private void handleCredentialUpdate(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String fileName = HttpRequestUtil.headerValue(request, "fileName");
        String relativePath = HttpRequestUtil.headerValue(request, "relativeFilePath");
        String propertyName = HttpRequestUtil.headerValue(request, "propertyName");

        String cleanPath = relativePath.endsWith("/") ? relativePath : relativePath + "/";
        Path file = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, cleanPath, fileName);
        Map<String, String> props = parseProperties(file);
        String value = props.getOrDefault(propertyName, "");
        if (!propertyName.isBlank()) {
            setProcessEnv(propertyName, value);
        }
        respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(propertyName + " was reset") + "\"}");
    }

    private void loadVariables(boolean forceReload) {
        if (!forceReload && !variableData.isEmpty()) {
            return;
        }
        Path file = Path.of(System.getProperty("user.dir"), "data_files", "variable.properties");
        try {
            Map<String, String> loaded = parseProperties(file);
            variableData.clear();
            variableData.putAll(loaded);
        } catch (IOException ignored) {
        }
    }

    private static Map<String, String> parseProperties(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        Map<String, String> data = new ConcurrentHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            data.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return data;
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("misc/")) {
            route = route.substring("misc/".length());
        }
        if ("misc".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
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

    @SuppressWarnings("unchecked")
    private static void setProcessEnv(String key, String value) {
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

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String token : value.split(",")) {
            if (!token.isBlank()) {
                values.add(token.trim());
            }
        }
        return values;
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

