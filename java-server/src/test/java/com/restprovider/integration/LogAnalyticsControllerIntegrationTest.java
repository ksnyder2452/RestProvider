package com.restprovider.integration;

import com.restprovider.controllers.LogAnalyticsController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogAnalyticsControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        LogAnalyticsController.CommandRunner runner = (command, args) -> "ok";
        LogAnalyticsController.FileContentReader reader = path -> "{\"Message\": \"Pipeline failed due to timeout\"}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new LogAnalyticsController(validator, runner, reader));
        registry.setControllerEnabled("LogAnalytics", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/loganalytics/message");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldMatchStartsWith() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/loganalytics/message/startswith");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "log_proj");
        request.addHeader("testcaseName", "tc1");
        request.addHeader("runId", "run-1");
        request.addHeader("analyticsWorkspaceId", "ws1");
        request.addHeader("expectedMessage", "Pipeline failed");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Pipeline failed"));
    }

    @Test
    void shouldReturn406WhenEndsWithDoesNotMatch() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/loganalytics/message/endswith");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", "log_proj");
        request.addHeader("testcaseName", "tc1");
        request.addHeader("runId", "run-1");
        request.addHeader("analyticsWorkspaceId", "ws1");
        request.addHeader("expectedMessage", "not-matching");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(406, response.getCode());
    }

    @Test
    void shouldSupportStartsWithAliasAndQueryInputs() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/loganalytics/message/starts-with?passCode=valid-passcode&projectName=log_proj"
                        + "&testcaseName=tc1&run=run-1&workspace=ws1&expected=Pipeline");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Pipeline failed"));
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/loganalytics/message");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
