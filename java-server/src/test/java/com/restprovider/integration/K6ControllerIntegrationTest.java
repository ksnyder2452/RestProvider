package com.restprovider.integration;

import com.restprovider.controllers.K6Controller;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class K6ControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        K6Controller.CommandRunner runner = (command, args) -> {
            if ("version".equals(args)) {
                return "k6 v0.52.0";
            }
            return "run ok";
        };
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new K6Controller(runner));
        registry.setControllerEnabled("K6", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnK6Version() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/k6/version");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("k6 v0.52.0"));
    }

    @Test
    void shouldSupportRunAliasWithQueryParameters() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/k6/run?scriptName=test.js&projectName=proj&testcaseName=tc1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("run ok"));
    }

    @Test
    void shouldReturnBadRequestWhenScriptMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/k6");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
