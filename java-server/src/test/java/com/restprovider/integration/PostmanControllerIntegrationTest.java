package com.restprovider.integration;

import com.restprovider.controllers.PostmanController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostmanControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        PostmanController.CommandRunner runner = (command, args) -> "Logged in successfully";
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new PostmanController(validator, runner));
        registry.setControllerEnabled("Postman", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/postman/status");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldReturnPostmanStatusWhenLoginSucceeds() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/postman/status");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("postmanUser", "any-user");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Logged in successfully"));
    }

    @Test
    void shouldSupportStatusAliasAndQueryString() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/postman/login/status?passCode=valid-passcode&user=any-user&apiKey=abc");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
    }
}
