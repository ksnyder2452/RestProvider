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
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class SQLServerController extends BaseController {
    private final PasscodeValidator passcodeValidator;

    public SQLServerController() {
        this(new EnvPasscodeValidator());
    }

    public SQLServerController(PasscodeValidator passcodeValidator) {
        super("SQLServer");
        this.passcodeValidator = passcodeValidator;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        if (!validatePassCode(request, response)) {
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            int result = executeQueryToFile(request, "sqlserver_query_result.txt");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":" + result + "}");
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "ddl".equalsIgnoreCase(route)) {
            int rows = executeUpdate(request);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rows + "\"}");
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            int rows = executeUpdate(request);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rows + "\"}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            int rows = executeUpdate(request);
            respondJson(response, HttpStatus.SC_OK, "{\"RowCount\":\"" + rows + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "blob".equalsIgnoreCase(route)) {
            String fileName = HttpRequestUtil.headerValue(request, "fileName");
            writeBlobToFile(request, fileName);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity("Downloaded " + fileName, ContentType.TEXT_PLAIN));
            return;
        }

        super.handle(request, response, subPath);
    }

    private int executeQueryToFile(ClassicHttpRequest request, String suffix) {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String testcaseName = HttpRequestUtil.headerValue(request, "testcaseName");
        String sql = HttpRequestUtil.headerValue(request, "sql_statement");
        String conn = HttpRequestUtil.headerValue(request, "sql_connection");
        String outputFile = HttpRequestUtil.headerValue(request, "outputFile");

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

    private int executeUpdate(ClassicHttpRequest request) {
        String sql = HttpRequestUtil.headerValue(request, "sql_statement");
        String conn = HttpRequestUtil.headerValue(request, "sql_connection");
        try (Connection c = DriverManager.getConnection(conn);
             Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        } catch (Exception ex) {
            throw new IllegalStateException("SQLServer update failed: " + ex.getMessage(), ex);
        }
    }

    private void writeBlobToFile(ClassicHttpRequest request, String fileName) {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String sql = HttpRequestUtil.headerValue(request, "sql_statement");
        String conn = HttpRequestUtil.headerValue(request, "sql_connection");
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

    private boolean validatePassCode(ClassicHttpRequest request, ClassicHttpResponse response) {
        String passCode = HttpRequestUtil.headerValue(request, "passCode");
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

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }
}

