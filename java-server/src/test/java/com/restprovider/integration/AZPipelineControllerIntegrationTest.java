package com.restprovider.integration;

import com.restprovider.controllers.AZPipelineController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;

class AZPipelineControllerIntegrationTest {
    private ControllerDispatcher dispatcher;
    private AtomicReference<String> lastArgs;

    @BeforeEach
    void setup() {
        lastArgs = new AtomicReference<>("");
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        AZPipelineController.CommandRunner runner =
                (command, args) -> {
                    lastArgs.set(args);
                    return "{\"result\":\"succeeded\",\"status\":\"completed\"}";
                };
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new AZPipelineController(validator, runner));
        registry.setControllerEnabled("AZPipeline", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectRequestWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/azpipeline/pipeline/runs");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldReturnRunDetailsWhenPasscodeIsValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/azpipeline/pipeline/run");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("organization", "example-org");
        request.addHeader("project", "example-project");
        request.addHeader("runId", "123");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("succeeded"));
    }

    @Test
    void shouldSupportQueryParametersForRunDetails() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
            "/api/azpipeline/pipeline/run?organization=example-org&project=example-project&runId=456&passcode=valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(lastArgs.get().contains("--id \"456\""));
        Assertions.assertTrue(lastArgs.get().contains("--organization \"https://dev.azure.com/example-org/\""));
    }

    @Test
    void shouldAllowPipelineRunByPipelineId() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/azpipeline/pipeline/run");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("organization", "example-org");
        request.addHeader("project", "example-project");
        request.addHeader("pipelineId", "999");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(lastArgs.get().contains("pipelines run --id \"999\""));
    }
}
