package com.restprovider.integration;

import com.restprovider.controllers.DataFactoryController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataFactoryControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        DataFactoryController.CommandRunner runner = (command, args) ->
                "{\"status\":\"Succeeded\",\"message\":\"done\"}";
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new DataFactoryController(validator, runner));
        registry.setControllerEnabled("DataFactory", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectRequestWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/datafactory/pipelines");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldValidatePipelineStatusWhenPasscodeIsValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/datafactory/pipeline/status");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("pipelineRunId", "abc");
        request.addHeader("expectedStatus", "Succeeded");
        request.addHeader("factoryName", "test-factory");
        request.addHeader("resourceGroupName", "test-rg");

        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Pipeline status was correct"));
    }

    @Test
    void shouldSupportQueryStringInputsForStatus() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/datafactory/pipeline/status?passCode=valid-passcode&runId=abc&expectedStatus=Succeeded"
                        + "&factoryName=test-factory&resourceGroup=test-rg");

        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Pipeline status was correct"));
    }

    @Test
    void shouldReturnBadRequestWhenRunStatusMissingRunId() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/datafactory/pipeline/status");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("expectedStatus", "Succeeded");
        request.addHeader("factoryName", "test-factory");
        request.addHeader("resourceGroupName", "test-rg");

        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
