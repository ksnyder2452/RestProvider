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

        if ("GET".equalsIgnoreCase(method)
                && ("excel/all".equalsIgnoreCase(route) || "excel/bycoordinate".equalsIgnoreCase(route))) {
            String passCode = HttpRequestUtil.headerValue(request, "passCode");
            if (!passcodeValidator.isValid(passCode)) {
            logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
            respondText(response, HttpStatus.SC_OK, "Passcode failure");
                return;
            }

            String projectName = HttpRequestUtil.headerValue(request, "projectName");
            String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));
            String relativeInput = HttpRequestUtil.headerValue(request, "relativeInputFilePath");
            String worksheetName = HttpRequestUtil.headerValue(request, "worksheetName");
            String deleteIfExists = HttpRequestUtil.headerValue(request, "deleteIfExists");

            Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName);
            Files.createDirectories(tempFolder);

            Path inputPath = tempFolder.resolve(relativeInput).normalize();
            String defaultOutputName = testcaseName + "_excel.csv";
            String outputFile = headerOrDefault(request, "outputFile", defaultOutputName);
            Path outputPath = tempFolder.resolve(outputFile).normalize();

            if (Files.exists(outputPath) && "Yes".equalsIgnoreCase(deleteIfExists)) {
                Files.delete(outputPath);
            }

            String content;
            if ("excel/all".equalsIgnoreCase(route)) {
                content = readAll(inputPath, worksheetName);
            } else {
                int rowStart = parseIntOrDefault(HttpRequestUtil.headerValue(request, "rowStart"), 1);
                int rowEnd = parseIntOrDefault(HttpRequestUtil.headerValue(request, "rowEnd"), rowStart);
                String colStart = HttpRequestUtil.headerValue(request, "colStart");
                String colEnd = HttpRequestUtil.headerValue(request, "colEnd");
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

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static void respondText(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    }
}

