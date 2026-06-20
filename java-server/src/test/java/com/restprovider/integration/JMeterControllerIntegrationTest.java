package com.restprovider.integration;

import com.restprovider.controllers.JMeterController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JMeterControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        JMeterController.CommandRunner runner = (command, args) -> {
            if (args.contains("-v")) {
                return "jmeter 5.6.0";
            }
            return "run ok";
        };
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new JMeterController(runner));
        registry.setControllerEnabled("JMeter", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnJMeterVersion() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/jmeter/version");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("jmeter 5.6.0"));
    }

    @Test
    void shouldSupportRunAliasWithQueryParameters() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/jmeter/run?scriptName=test.jmx&projectName=proj&testcaseName=tc1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("run ok"));
    }

    @Test
    void shouldReturnBadRequestWhenScriptMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/jmeter");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
