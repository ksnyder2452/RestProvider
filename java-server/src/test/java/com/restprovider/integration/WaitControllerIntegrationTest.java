package com.restprovider.integration;

import com.restprovider.controllers.WaitController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WaitControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new WaitController(validator));
        registry.setControllerEnabled("Wait", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldSleepUsingRouteSegmentAlias() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/wait/sleep/0");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Slept for 0 seconds"));
    }

    @Test
    void shouldRejectProtectedWaitRouteWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/wait/waitforstatus");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Passcode failure"));
    }
}