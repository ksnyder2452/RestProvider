package com.restprovider.integration;

import com.restprovider.controllers.PowerBIController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowerBIControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        PowerBIController.TokenProvider tokenProvider = () -> "token-1";
        PowerBIController.HttpInvoker invoker = (method, endpoint, token) -> "{\"status\":\"ok\"}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new PowerBIController(validator, tokenProvider, invoker));
        registry.setControllerEnabled("PowerBI", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnPasscodeFailureWhenMissingPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/powerbi");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertEquals("Passcode failure", TestResponseUtil.body(response));
    }

    @Test
    void shouldWriteOutputWhenPasscodeValid() throws Exception {
        String project = "pbi_" + System.nanoTime();
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/powerbi");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", project);
        request.addHeader("powerBIRequest", "groups");
        request.addHeader("organization", "org1");
        request.addHeader("apiVersion", "1.0");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path output = Path.of(System.getProperty("user.dir"), "data_files", "temp", project, "powerBI_get_response.txt");
        Assertions.assertTrue(Files.exists(output));
        Assertions.assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("status"));
    }

    @Test
    void shouldSupportRequestAliasAndQueryString() throws Exception {
        String project = "pbi_" + System.nanoTime();
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/powerbi/request?passCode=valid-passcode&project=" + project
                        + "&request=groups&org=org1&version=1.0&token=abc-token");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path output = Path.of(System.getProperty("user.dir"), "data_files", "temp", project, "powerBI_get_response.txt");
        Assertions.assertTrue(Files.exists(output));
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/powerbi");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
