package com.restprovider.integration;

import com.restprovider.controllers.OfficeController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficeControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new OfficeController(validator));
        registry.setControllerEnabled("Office", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReadExcelAllAndWriteCsv() throws Exception {
        String project = "office_" + System.nanoTime();
        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project);
        Files.createDirectories(tempFolder);
        Path workbookPath = tempFolder.resolve("sample.xlsx");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Name");
            sheet.getRow(0).createCell(1).setCellValue("Age");
            sheet.createRow(1).createCell(0).setCellValue("Alex");
            sheet.getRow(1).createCell(1).setCellValue("30");
            workbook.write(Files.newOutputStream(workbookPath));
        }

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/office/excel/all");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", project);
        request.addHeader("testcaseName", "tc1");
        request.addHeader("relativeInputFilePath", "sample.xlsx");
        request.addHeader("worksheetName", "Data");
        request.addHeader("deleteIfExists", "Yes");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path output = tempFolder.resolve("tc1_excel.csv");
        String csv = Files.readString(output, StandardCharsets.UTF_8);
        Assertions.assertTrue(csv.contains("Name,Age"));
        Assertions.assertTrue(csv.contains("Alex,30"));
    }

    @Test
    void shouldSupportRangeAliasWithQueryParams() throws Exception {
        String project = "office_" + System.nanoTime();
        Path tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project);
        Files.createDirectories(tempFolder);
        Path workbookPath = tempFolder.resolve("sample.xlsx");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("A1");
            sheet.createRow(1).createCell(0).setCellValue("A2");
            workbook.write(Files.newOutputStream(workbookPath));
        }

        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/office/excel/range?passCode=valid-passcode&projectName=" + project
                        + "&testcaseName=tc2&inputFile=sample.xlsx&sheet=Data&startRow=1&endRow=2"
                        + "&startCol=A&endCol=A");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path output = tempFolder.resolve("tc2_excel.csv");
        Assertions.assertTrue(Files.exists(output));
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/office/excel/all");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }

    @Test
    void shouldRejectWhenPasscodeInvalid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/office/excel/all?passcode=wrong");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }
}
