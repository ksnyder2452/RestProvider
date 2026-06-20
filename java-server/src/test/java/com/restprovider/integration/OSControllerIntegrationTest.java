package com.restprovider.integration;

import com.restprovider.controllers.OSController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OSControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        OSController.CommandRunner runner = (command, args) -> "ok";
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new OSController(validator, runner));
        registry.setControllerEnabled("OS", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnYear() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/os/year?dateFormat=yyyy");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"year\":"));
    }

    @Test
    void shouldReturnRootFolder() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/os/folder");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("rootDir"));
    }

    @Test
    void shouldBlockSensitiveEnvironmentVariableRead() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/os/variable");
        request.addHeader("varName", "git_token");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("NotFound"));
    }

    @Test
    void shouldRejectScheduleWhenPasscodeInvalid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/os/insightlink/session/schedule");
        request.addHeader("projectName", "proj1");
        request.addHeader("sessionName", "sessionA");
        request.addHeader("schedule", "*/1 * * * *");
        request.addHeader("maxRuns", "1");
        request.addHeader("passCode", "wrong");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }

    @Test
    void shouldSubmitScheduleWhenScriptExists() throws Exception {
        Path archive = Path.of(System.getProperty("user.dir"), "data_files", "temp", "proj2", "Archives");
        Files.createDirectories(archive);
        Files.writeString(archive.resolve("sessionB_container.sh"), "#!/bin/sh\necho hi\n", StandardCharsets.UTF_8);

        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
            "POST",
            "/api/os/session/schedule?project=proj2&session=sessionB&interval=*/1%20*%20*%20*%20*&runs=1&passcode=valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("has been submitted"));
    }

    @Test
    void shouldRejectFolderCreateWithoutFolderName() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/os/folder/create?project=proj3");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("folderName"));
    }
}
