package com.restprovider.integration;

import com.restprovider.controllers.AzureController;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.domain.azure.dto.AzureCommandRequest;
import com.restprovider.domain.azure.dto.AzureCommandResponse;
import com.restprovider.domain.azure.service.AzureService;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {

        AzureService azureService = new AzureService() {
            @Override
            public AzureCommandResponse setExtensionsNoPrompt(AzureCommandRequest request) {
                return new AzureCommandResponse("dynamic install configured", HttpStatus.SC_OK);
            }

            @Override
            public AzureCommandResponse checkLogin(AzureCommandRequest request) {
                return new AzureCommandResponse("{\"state\": \"Enabled\"}", HttpStatus.SC_OK);
            }

            @Override
            public AzureCommandResponse runCommand(AzureCommandRequest request, String commandArguments) {
                return new AzureCommandResponse("ran: " + commandArguments, HttpStatus.SC_OK);
            }
        };

        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new AzureController(azureService, validator));
        registry.setControllerEnabled("Azure", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectAzureRequestWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/azure/check/azurecli");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldRunAzureExtensionConfigWhenPasscodeIsValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/azure/az/extensions/config");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("dynamic install configured"));
    }

    @Test
    void shouldReturnAzureLoginCheckResultWhenPasscodeIsValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/azure/check/azurecli");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Enabled"));
    }

    @Test
    void shouldRunGenericAzureCommandWhenPasscodeIsValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/azure/az/command");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("command", "group list --output json");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("group list --output json"));
    }

    @Test
    void shouldRunGenericAzureCommandFromQueryString() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/azure/az/command?passCode=valid-passcode&command=account%20list%20-o%20json");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("account%20list%20-o%20json"));
    }
}
