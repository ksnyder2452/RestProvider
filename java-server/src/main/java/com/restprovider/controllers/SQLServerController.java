package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
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

/**
 * Controller for the SQLServer integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class SQLServerController extends BaseController {
    private final PasscodeValidator passcodeValidator;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public SQLServerController() {
        this(new EnvPasscodeValidator());
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public SQLServerController(PasscodeValidator passcodeValidator) {
        super("SQLServer");
        this.passcodeValidator = passcodeValidator;
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
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if (!validatePassCode(request, response, query)) {
            return;
        }

        if (("GET".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && ("".equals(route) || "query".equalsIgnoreCase(route))) {
            int result = executeQueryToFile(request, query, "sqlserver_query_result.txt");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":" + result + "}");
            return;
        }

        if (("PUT".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && "ddl".equalsIgnoreCase(route)) {
            int rows = executeUpdate(request, query);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rows + "\"}");
            return;
        }

        if (("PUT".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod()))
                && ("".equals(route) || "dml".equalsIgnoreCase(route) || "nonquery".equalsIgnoreCase(route))) {
            int rows = executeUpdate(request, query);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rows + "\"}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod())
                && ("".equals(route) || "nonquery".equalsIgnoreCase(route))) {
            int rows = executeUpdate(request, query);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rows + "\"}");
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

    private int executeQueryToFile(ClassicHttpRequest request, Map<String, String> query, String suffix) {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "sqlserver");
        String testcaseName = readValue(request, query, "testcaseName", "testcase", "testCase");
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        String conn = readValue(request, query, "sql_connection", "connection", "jdbcUrl");
        String outputFile = readValue(request, query, "outputFile");

        requireThrow("sql_statement", sql);
        requireThrow("sql_connection", conn);

        Path base = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
        try {
            Files.createDirectories(base);
            String defaultName = (testcaseName == null ? "" : testcaseName.replace(" ", "_")) + "_" + suffix;
            Path out = outputFile == null || outputFile.isBlank() ? base.resolve(defaultName) : base.resolve(outputFile);
            Files.deleteIfExists(out);

            int rows = 0;
            try (Connection c = DriverManager.getConnection(conn);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    rows++;
                    StringBuilder line = new StringBuilder();
                    for (int i = 1; i <= columns; i++) {
                        if (i > 1) {
                            line.append(" | ");
                        }
                        Object value = rs.getObject(i);
                        line.append(value == null ? "" : value.toString());
                    }
                    Files.writeString(out, line + System.lineSeparator(), StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalStateException("SQLServer query failed: " + ex.getMessage(), ex);
        }
    }

    private int executeUpdate(ClassicHttpRequest request, Map<String, String> query) {
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        String conn = readValue(request, query, "sql_connection", "connection", "jdbcUrl");

        requireThrow("sql_statement", sql);
        requireThrow("sql_connection", conn);

        try (Connection c = DriverManager.getConnection(conn);
             Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        } catch (Exception ex) {
            throw new IllegalStateException("SQLServer update failed: " + ex.getMessage(), ex);
        }
    }

    private void writeBlobToFile(ClassicHttpRequest request, Map<String, String> query, String fileName) {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "sqlserver");
        String sql = readValue(request, query, "sql_statement", "sql", "query");
        String conn = readValue(request, query, "sql_connection", "connection", "jdbcUrl");

        requireThrow("sql_statement", sql);
        requireThrow("sql_connection", conn);

        Path out = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, fileName);
        try {
            Files.createDirectories(out.getParent());
            try (Connection c = DriverManager.getConnection(conn);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                if (rs.next()) {
                    byte[] data;
                    try {
                        data = rs.getBytes(2);
                    } catch (Exception ex) {
                        data = rs.getBytes(1);
                    }
                    if (data != null) {
                        Files.write(out, data);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("SQLServer blob download failed: " + ex.getMessage(), ex);
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
        if (route.startsWith("sqlserver/")) {
            route = route.substring("sqlserver/".length());
        }
        if ("sqlserver".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
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



