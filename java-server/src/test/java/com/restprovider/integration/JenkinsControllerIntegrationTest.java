package com.restprovider.integration;

import com.restprovider.controllers.JenkinsController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JenkinsControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        JenkinsController.HttpInvoker invoker = (method, endpoint, user, token) ->
                "{\"method\":\"" + method + "\",\"status\":\"ok\"}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new JenkinsController(validator, invoker));
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/jenkins");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldAllowPutWhenPasscodeValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("PUT", "/api/jenkins");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("subURI", "job/test");
        request.addHeader("serverName", "jenkins.local");
        request.addHeader("serverPort", "8080");

        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("PUT"));
    }
}
