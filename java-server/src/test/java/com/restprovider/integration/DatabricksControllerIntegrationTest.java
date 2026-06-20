package com.restprovider.integration;

import com.restprovider.controllers.DatabricksController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabricksControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        DatabricksController.CommandRunner runner = (command, args) -> {
            if (args.contains("clusters get")) {
                return "{\"state\": \"RUNNING\",}";
            }
            return "ok";
        };
        DatabricksController.HttpInvoker invoker = (method, endpoint, token, body) -> "{\"status\":\"ok\"}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new DatabricksController(validator, runner, invoker));
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/databricks");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldRunDatabricksRunList() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/databricks/run");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("limitTo", "10");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("ok"));
    }
}
