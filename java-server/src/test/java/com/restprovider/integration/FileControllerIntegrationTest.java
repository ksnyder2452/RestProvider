package com.restprovider.integration;

import com.restprovider.controllers.FileController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        FileController.CommandRunner runner = (command, args) -> "ok";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new FileController(validator, runner));
        registry.setControllerEnabled("File", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/file/exists");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldHandleBinaryDiff() throws Exception {
        String project = "file_" + System.nanoTime();
        Path root = Path.of(System.getProperty("user.dir"), "data_files", "temp", project);
        Files.createDirectories(root);
        Files.writeString(root.resolve("a.bin"), "abc", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("b.bin"), "abc", StandardCharsets.UTF_8);

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("PUT", "/api/file/diff/binary");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", project);
        request.addHeader("file1", "a.bin");
        request.addHeader("file2", "b.bin");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("match"));
    }

    @Test
    void shouldUploadFileToLocalRoute() throws Exception {
        String project = "upload_" + System.nanoTime();
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
            "POST",
            "/api/file/upload/local?passcode=valid-passcode&project=" + project + "&file=payload.txt");
        request.setEntity(new StringEntity("hello", ContentType.TEXT_PLAIN));
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path saved = Path.of(System.getProperty("user.dir"), "data_files", "temp", project, "payload.txt");
        Assertions.assertTrue(Files.exists(saved));
        Assertions.assertEquals("hello", Files.readString(saved, StandardCharsets.UTF_8));
    }

    @Test
    void shouldRejectExistsWhenFileMissingFromRequest() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/file/exists?passcode=valid-passcode&project=test");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("fileName"));
    }
}
