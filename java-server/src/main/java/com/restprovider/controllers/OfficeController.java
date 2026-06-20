package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class OfficeController extends BaseController {
    private final PasscodeValidator passcodeValidator;

    public OfficeController() {
        this(new EnvPasscodeValidator());
    }

    public OfficeController(PasscodeValidator passcodeValidator) {
        super("Office");
        this.passcodeValidator = passcodeValidator;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if ("GET".equalsIgnoreCase(method)
                && ("excel/all".equalsIgnoreCase(route)
                || "excel/bycoordinate".equalsIgnoreCase(route)
                || "excel/range".equalsIgnoreCase(route)
                || "excel/by-coordinate".equalsIgnoreCase(route))) {
            String passCode = readValue(request, query, "passCode", "passcode");
            if (!passcodeValidator.isValid(passCode)) {
                logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
                response.setEntity(new StringEntity("{\"passCodeResult\":\"Passcode failure\"}",
                        ContentType.APPLICATION_JSON));
                return;
            }

            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "office");
            String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "run"));
            String relativeInput = readValue(request, query, "relativeInputFilePath", "inputFile", "file");
            String worksheetName = readValue(request, query, "worksheetName", "sheet", "sheetName");
            String deleteIfExists = defaultValue(readValue(request, query, "deleteIfExists", "delete"), "No");

            if (!require(response, "relativeInputFilePath", relativeInput)
                    || !require(response, "worksheetName", worksheetName)) {
                return;
            }

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);

            Path inputPath = tempFolder.resolve(relativeInput).normalize();
            String defaultOutputName = testcaseName + "_excel.csv";
            String outputFile = defaultValue(readValue(request, query, "outputFile"), defaultOutputName);
            Path outputPath = tempFolder.resolve(outputFile).normalize();

            if (Files.exists(outputPath) && "Yes".equalsIgnoreCase(deleteIfExists)) {
                Files.delete(outputPath);
            }

            String content;
            if ("excel/all".equalsIgnoreCase(route)) {
                content = readAll(inputPath, worksheetName);
            } else {
                int rowStart = parseIntOrDefault(readValue(request, query, "rowStart", "startRow"), 1);
                int rowEnd = parseIntOrDefault(readValue(request, query, "rowEnd", "endRow"), rowStart);
                String colStart = readValue(request, query, "colStart", "startCol", "columnStart");
                String colEnd = readValue(request, query, "colEnd", "endCol", "columnEnd");
                content = readByCoordinate(inputPath, worksheetName, rowStart, rowEnd, colStart, colEnd);
            }

            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
            respondText(response, HttpStatus.SC_OK, outputPath + " has been created.");
            return;
        }

        super.handle(request, response, subPath);
    }

    private static String readAll(Path xlsxPath, String worksheetName) {
        DataFormatter formatter = new DataFormatter();
        List<String> lines = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(xlsxPath))) {
            Sheet sheet = workbook.getSheet(worksheetName);
            if (sheet == null) {
                return "";
            }
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                int lastCell = row.getLastCellNum();
                if (lastCell < 0) {
                    continue;
                }
                for (int i = 0; i < lastCell; i++) {
                    Cell cell = row.getCell(i);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                lines.add(String.join(",", values));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed reading xlsx: " + ex.getMessage(), ex);
        }
        return String.join(System.lineSeparator(), lines) + (lines.isEmpty() ? "" : System.lineSeparator());
    }

    private static String readByCoordinate(Path xlsxPath, String worksheetName,
                                           int rowStart, int rowEnd, String colStart, String colEnd) {
        DataFormatter formatter = new DataFormatter();
        List<String> lines = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(xlsxPath))) {
            Sheet sheet = workbook.getSheet(worksheetName);
            if (sheet == null) {
                return "";
            }

            int startCol = columnToIndex(colStart);
            int endCol = columnToIndex(colEnd);
            for (int col = startCol; col <= endCol; col++) {
                List<String> values = new ArrayList<>();
                for (int rowNum = rowStart; rowNum <= rowEnd; rowNum++) {
                    Row row = sheet.getRow(rowNum - 1);
                    Cell cell = row == null ? null : row.getCell(col);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                lines.add(String.join(",", values));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed reading xlsx range: " + ex.getMessage(), ex);
        }
        return String.join(System.lineSeparator(), lines) + (lines.isEmpty() ? "" : System.lineSeparator());
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("office/")) {
            route = route.substring("office/".length());
        }
        return route;
    }

    private static int columnToIndex(String col) {
        String c = (col == null ? "A" : col).trim().toUpperCase();
        int result = 0;
        for (int i = 0; i < c.length(); i++) {
            result = result * 26 + (c.charAt(i) - 'A' + 1);
        }
        return Math.max(0, result - 1);
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String safeName(String input) {
        return input == null ? "" : input.replace(" ", "_");
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
        respondText(response, HttpStatus.SC_BAD_REQUEST, "Missing required parameter: " + field);
        return false;
    }

    private static void respondText(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    }
}

