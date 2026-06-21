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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Controller for the CosmosDB integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class CosmosDBController extends BaseController {
    private static final Pattern NAME_PATTERN = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public CosmosDBController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public CosmosDBController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("CosmosDB");
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

        if (!("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))) {
            super.handle(request, response, subPath);
            return;
        }

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("".equals(route) || "query".equalsIgnoreCase(route))
                && ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))) {
            handleQuery(request, response, query);
            return;
        }
        if ("databases".equalsIgnoreCase(route)) {
            handleDatabases(request, response, query);
            return;
        }
        if ("database/container/details".equalsIgnoreCase(route)
                || "container/details".equalsIgnoreCase(route)) {
            handleContainerDetails(request, response, query);
            return;
        }
        if ("database/details".equalsIgnoreCase(route)) {
            handleDatabaseDetails(request, response, query);
            return;
        }
        if ("containers".equalsIgnoreCase(route)) {
            handleContainers(request, response, query);
            return;
        }
        if ("database/container".equalsIgnoreCase(route)) {
            handleDatabaseByContainerExact(request, response, query);
            return;
        }
        if ("database/container/matchonvalue".equalsIgnoreCase(route)) {
            handleContainerMatchOnValue(request, response, query);
            return;
        }
        if ("database/container/match".equalsIgnoreCase(route)) {
            handleContainerFuzzyMatch(request, response, query);
            return;
        }

        super.handle(request, response, subPath);
    }

    private void handleQuery(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "cosmos");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "cosmos_query"));
        String db = readValue(request, query, "cosmosDatabaseId", "databaseId", "databaseName", "database");
        String container = readValue(request, query, "cosmosContainerId", "containerId", "containerName", "container");
        String sql = readValue(request, query, "sql_statement", "query", "sql");

        if (!require(response, "databaseId", db) || !require(response, "containerId", container) || !require(response, "sql_statement", sql)) {
            return;
        }

        Path folder = tempProjectFolder(projectName);
        Files.createDirectories(folder);
        String outputFile = defaultValue(readValue(request, query, "outputFile"), testcaseName + "_cosmos_query_result.txt");
        Path outputPath = folder.resolve(outputFile);

        String args = "cosmosdb sql query"
                + rgArg(request, query)
                + accountArg(request, query)
                + " --database-name \"" + db + "\""
                + " --container-name \"" + container + "\""
                + " --query-text \"" + escapeQuotes(sql) + "\"";
        try {
            String result = commandRunner.run("az", args);
            Files.writeString(outputPath, result, StandardCharsets.UTF_8);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"CosmosDB read has completed\"}");
        } catch (Exception ex) {
            Files.writeString(outputPath, ex.getMessage(), StandardCharsets.UTF_8);
            respondJson(response, HttpStatus.SC_NOT_ACCEPTABLE,
                    "{\"Message\":\"" + JsonUtil.escape(ex.getMessage()) + "\"}");
        }
    }

    private void handleDatabases(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "cosmos");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "cosmos_databases"));
        Path folder = tempProjectFolder(projectName);
        Files.createDirectories(folder);
        Path outputPath = folder.resolve(defaultValue(readValue(request, query, "outputFile"), testcaseName + "_cosmos_database_list.txt"));

        String result = commandRunner.run("az", "cosmosdb sql database list" + rgArg(request, query) + accountArg(request, query));
        Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        respondJson(response, HttpStatus.SC_OK,
                "{\"databases\":[\"" + String.join("\",\"", extractNames(result)) + "\"]}");
    }

    private void handleContainerDetails(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String databaseName = readValue(request, query, "databaseName", "databaseId", "database");
        String containerName = readValue(request, query, "containerName", "containerId", "container");
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "cosmos");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "cosmos_container"));
        if (!require(response, "databaseName", databaseName) || !require(response, "containerName", containerName)) {
            return;
        }
        Path folder = tempProjectFolder(projectName);
        Files.createDirectories(folder);
        Path outputPath = folder.resolve(defaultValue(readValue(request, query, "outputFile"), testcaseName + "_cosmos_container_details.json"));

        String result = commandRunner.run("az", "cosmosdb sql container show"
                + rgArg(request, query)
                + accountArg(request, query)
                + " --database-name \"" + databaseName + "\""
                + " --name \"" + containerName + "\"");
        Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        respondText(response, HttpStatus.SC_OK, "Retrieved details for Cosmosdb Container " + containerName);
    }

    private void handleDatabaseDetails(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String databaseName = readValue(request, query, "databaseName", "databaseId", "database");
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "cosmos");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "cosmos_database"));
        if (!require(response, "databaseName", databaseName)) {
            return;
        }
        Path folder = tempProjectFolder(projectName);
        Files.createDirectories(folder);
        Path outputPath = folder.resolve(defaultValue(readValue(request, query, "outputFile"), testcaseName + "_cosmos_database_details.json"));

        String result = commandRunner.run("az", "cosmosdb sql database show"
                + rgArg(request, query)
                + accountArg(request, query)
                + " --name \"" + databaseName + "\"");
        Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        respondText(response, HttpStatus.SC_OK, "Retrieved details for Cosmosdb Database " + databaseName);
    }

    private void handleContainers(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String databaseId = readValue(request, query, "databaseId", "databaseName", "database");
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "cosmos");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "cosmos_containers"));
        if (!require(response, "databaseId", databaseId)) {
            return;
        }
        Path folder = tempProjectFolder(projectName);
        Files.createDirectories(folder);
        Path outputPath = folder.resolve(defaultValue(readValue(request, query, "outputFile"), testcaseName + "_cosmos_container_list.json"));

        String result = commandRunner.run("az", "cosmosdb sql container list"
                + rgArg(request, query)
                + accountArg(request, query)
                + " --database-name \"" + databaseId + "\"");
        Files.writeString(outputPath, result, StandardCharsets.UTF_8);
        respondJson(response, HttpStatus.SC_OK,
                "{\"containers\":[\"" + String.join("\",\"", extractNames(result)) + "\"]}");
    }

    private void handleDatabaseByContainerExact(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String filter = readValue(request, query, "filterForContainer", "containerName", "container");
        if (!require(response, "filterForContainer", filter)) {
            return;
        }
        MatchContext ctx = collectDatabaseContainerMap(request, query);
        List<String> matches = new ArrayList<>();
        for (DatabaseContainers pair : ctx.map) {
            for (String c : pair.containers) {
                if (c.equals(filter)) {
                    matches.add(pair.database);
                }
            }
        }
        respondText(response, HttpStatus.SC_OK, String.join(",", matches));
    }

    private void handleContainerMatchOnValue(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String parameterName = readValue(request, query, "parameterName", "field", "property");
        String parameterValue = readValue(request, query, "parameterValue", "value");
        if (!require(response, "parameterName", parameterName) || !require(response, "parameterValue", parameterValue)) {
            return;
        }
        MatchContext ctx = collectDatabaseContainerMap(request, query);
        Set<String> matchedContainers = new LinkedHashSet<>();

        for (DatabaseContainers pair : ctx.map) {
            for (String container : pair.containers) {
                String result = commandRunner.run("az", "cosmosdb sql query"
                    + rgArg(request, query)
                    + accountArg(request, query)
                        + " --database-name \"" + pair.database + "\""
                        + " --container-name \"" + container + "\""
                        + " --query-text \"select * from c\"");
                if (result.contains("\"" + parameterName + "\"")
                        && result.contains("\"" + parameterValue + "\"")) {
                    matchedContainers.add(container);
                }
            }
        }
        respondText(response, HttpStatus.SC_OK, String.join(",", matchedContainers));
    }

    private void handleContainerFuzzyMatch(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String filter = readValue(request, query, "filterForContainer", "containerName", "container");
        if (!require(response, "filterForContainer", filter)) {
            return;
        }
        MatchContext ctx = collectDatabaseContainerMap(request, query);
        List<String> matched = new ArrayList<>();
        for (DatabaseContainers pair : ctx.map) {
            for (String container : pair.containers) {
                if (levenshtein(container.toUpperCase(), filter.toUpperCase()) < 2) {
                    matched.add(pair.database);
                    matched.add(container);
                }
            }
        }
        respondText(response, HttpStatus.SC_OK, String.join(",", matched));
    }

    private MatchContext collectDatabaseContainerMap(ClassicHttpRequest request, Map<String, String> query)
            throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "cosmos");
        String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "cosmos_map"));
        Path folder = tempProjectFolder(projectName);
        Files.createDirectories(folder);
        Path outputPath = folder.resolve(defaultValue(readValue(request, query, "outputFile"), testcaseName + "_cosmos_database_list.txt"));

        String dbList = commandRunner.run("az", "cosmosdb sql database list" + rgArg(request, query) + accountArg(request, query));
        Files.writeString(outputPath, dbList + System.lineSeparator(), StandardCharsets.UTF_8);
        List<String> dbs = extractNames(dbList);

        List<DatabaseContainers> map = new ArrayList<>();
        for (String db : dbs) {
            String containersResult = commandRunner.run("az", "cosmosdb sql container list"
                    + rgArg(request, query)
                    + accountArg(request, query)
                    + " --database-name \"" + db + "\"");
            Files.writeString(outputPath, containersResult + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            map.add(new DatabaseContainers(db, extractNames(containersResult)));
        }
        return new MatchContext(outputPath, map);
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
        if (route.startsWith("cosmosdb/")) {
            route = route.substring("cosmosdb/".length());
        }
        if ("cosmosdb".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String safeName(String value) {
        return value == null ? "" : value.replace(" ", "_");
    }

    private static String escapeQuotes(String value) {
        return (value == null ? "" : value).replace("\"", "\\\"");
    }

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
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

    private static boolean require(ClassicHttpResponse response, String field, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"" + JsonUtil.escape("Missing required parameter: " + field) + "\"}");
        return false;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static Path tempProjectFolder(String projectName) {
        return Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName == null ? "" : projectName);
    }

    private static List<String> extractNames(String json) {
        List<String> names = new ArrayList<>();
        Matcher m = NAME_PATTERN.matcher(json == null ? "" : json);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private static String rgArg(ClassicHttpRequest request, Map<String, String> query) {
        String rg = defaultValue(readValue(request, query, "resourceGroupName", "resourceGroup", "rg"),
                env("RESTPROVIDER_COSMOS_RESOURCE_GROUP"));
        return rg.isBlank() ? "" : " --resource-group \"" + rg + "\"";
    }

    private static String accountArg(ClassicHttpRequest request, Map<String, String> query) {
        String acc = defaultValue(readValue(request, query, "cosmosdbAccountName", "accountName", "account"),
                env("RESTPROVIDER_COSMOS_ACCOUNT_NAME"));
        return acc.isBlank() ? "" : " --account-name \"" + acc + "\"";
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    private static void respondText(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }

    private static final class DatabaseContainers {
        private final String database;
        private final List<String> containers;

        private DatabaseContainers(String database, List<String> containers) {
            this.database = database;
            this.containers = containers;
        }
    }

    private static final class MatchContext {
        private final Path outputPath;
        private final List<DatabaseContainers> map;

        private MatchContext(Path outputPath, List<DatabaseContainers> map) {
            this.outputPath = outputPath;
            this.map = map;
        }
    }
}



