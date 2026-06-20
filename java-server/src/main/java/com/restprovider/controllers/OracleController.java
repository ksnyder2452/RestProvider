package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class OracleController extends BaseController {
    private final PasscodeValidator passcodeValidator;

    public OracleController() {
        this(new EnvPasscodeValidator());
    }

    public OracleController(PasscodeValidator passcodeValidator) {
        super("Oracle");
        this.passcodeValidator = passcodeValidator;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("GET".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && ("".equals(route) || "query".equalsIgnoreCase(route))) {
            String result = executeQueryToFile(request, query);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(result, ContentType.TEXT_PLAIN));
            return;
        }

        if (("PUT".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && "ddl".equalsIgnoreCase(route)) {
            int rows = executeUpdate(request, query);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":" + rows + "}");
            return;
        }

        if (("PUT".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && ("".equals(route) || "dml".equalsIgnoreCase(route) || "nonquery".equalsIgnoreCase(route))) {
            int rows = executeUpdate(request, query);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":" + rows + "}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod())
                && ("".equals(route) || "nonquery".equalsIgnoreCase(route))) {
            int rows = executeUpdate(request, query);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":" + rows + "}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && ("blob".equalsIgnoreCase(route) || "blob/download".equalsIgnoreCase(route))) {
            String fileName = readValue(request, query, "fileName", "outputFile", "name");
            if (!require(response, "fileName", fileName)) {
                return;
            }
            writeBlobToFile(request, query, fileName);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("Downloaded " + fileName, ContentType.TEXT_PLAIN));
            return;
        }

        super.handle(request, response, subPath);
    }

    private String executeQueryToFile(ClassicHttpRequest request, Map<String, String> query) {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "oracle");
        String testcaseName = readValue(request, query, "testcaseName", "testcase", "testCase");
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        String sqlConnection = readValue(request, query, "sql_connection", "connection", "jdbcUrl");
        String outputFile = readValue(request, query, "outputFile");
        String user = defaultValue(readValue(request, query, "oracleUser", "user", "username"), System.getenv("oracle_user"));
        String password = defaultValue(readValue(request, query, "oraclePassword", "password", "pwd"), System.getenv("oracle_password"));

        requireThrow("sql_statement", sql);
        requireThrow("sql_connection", sqlConnection);
        requireThrow("oracleUser", user);
        requireThrow("oraclePassword", password);

        String jdbcUrl = toOracleJdbcUrl(sqlConnection);
        Path base = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        try {
            Files.createDirectories(base);
            String defaultName = (testcaseName == null ? "" : testcaseName.replace(" ", "_")) + "_oracle_query_result.txt";
            Path out = outputFile == null || outputFile.isBlank() ? base.resolve(defaultName) : base.resolve(outputFile);
            Files.deleteIfExists(out);

            int recordCount = 0;
            try (Connection c = DriverManager.getConnection(jdbcUrl, user, password);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    recordCount++;
                    StringBuilder line = new StringBuilder();
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) {
                            line.append(" | ");
                        }
                        Object val = rs.getObject(i);
                        line.append(val == null ? "" : val.toString());
                    }
                    Files.writeString(out, line + System.lineSeparator(), StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
            }
            return recordCount + " rows were retured";
        } catch (Exception ex) {
            throw new IllegalStateException("Oracle query failed: " + ex.getMessage(), ex);
        }
    }

    private int executeUpdate(ClassicHttpRequest request, Map<String, String> query) {
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        String sqlConnection = readValue(request, query, "sql_connection", "connection", "jdbcUrl");
        String user = defaultValue(readValue(request, query, "oracleUser", "user", "username"), System.getenv("oracle_user"));
        String password = defaultValue(readValue(request, query, "oraclePassword", "password", "pwd"), System.getenv("oracle_password"));

        requireThrow("sql_statement", sql);
        requireThrow("sql_connection", sqlConnection);
        requireThrow("oracleUser", user);
        requireThrow("oraclePassword", password);

        try (Connection c = DriverManager.getConnection(toOracleJdbcUrl(sqlConnection), user, password);
             Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        } catch (Exception ex) {
            throw new IllegalStateException("Oracle update failed: " + ex.getMessage(), ex);
        }
    }

    private void writeBlobToFile(ClassicHttpRequest request, Map<String, String> query, String fileName) {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "oracle");
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        String sqlConnection = readValue(request, query, "sql_connection", "connection", "jdbcUrl");
        int blobColumn = parseIntOrDefault(readValue(request, query, "blob_column_id", "blobColumnId", "blobColumn"), 0) + 1;
        String user = defaultValue(readValue(request, query, "oracleUser", "user", "username"), System.getenv("oracle_user"));
        String password = defaultValue(readValue(request, query, "oraclePassword", "password", "pwd"), System.getenv("oracle_password"));

        requireThrow("sql_statement", sql);
        requireThrow("sql_connection", sqlConnection);
        requireThrow("oracleUser", user);
        requireThrow("oraclePassword", password);

        Path out = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, fileName);
        try {
            Files.createDirectories(out.getParent());
            try (Connection c = DriverManager.getConnection(toOracleJdbcUrl(sqlConnection), user, password);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                if (rs.next()) {
                    byte[] data = rs.getBytes(blobColumn);
                    if (data != null) {
                        Files.write(out, data);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Oracle blob retrieval failed: " + ex.getMessage(), ex);
        }
    }

    private static String toOracleJdbcUrl(String connection) {
        if (connection == null) {
            return "";
        }
        if (connection.startsWith("jdbc:")) {
            return connection;
        }
        return "jdbc:oracle:thin:@" + connection;
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
        if (route.startsWith("oracle/")) {
            route = route.substring("oracle/".length());
        }
        if ("oracle".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
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
                "{\"error\":\"Missing required parameter: " + field + "\"}");
        return false;
    }

    private static void requireThrow(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required parameter: " + field);
        }
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }
}

