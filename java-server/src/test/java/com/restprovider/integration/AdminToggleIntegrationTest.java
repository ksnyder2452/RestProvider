package com.restprovider.integration;

import com.restprovider.core.ControllerRegistry;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.DefaultRegistryFactory;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminToggleIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        ControllerRegistry registry = DefaultRegistryFactory.createDefaultRegistry();
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldDisableAndEnableControllerViaAdminRoute() throws Exception {
        BasicClassicHttpResponse beforeDisable = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/api/string/echo"), beforeDisable, TestHttpContexts.newContext());
        Assertions.assertEquals(403, beforeDisable.getCode());

        BasicClassicHttpResponse initialEnableResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(
            new BasicClassicHttpRequest("PUT", "/admin/controllers/string/enabled?value=true"),
            initialEnableResponse,
            TestHttpContexts.newContext());
        Assertions.assertEquals(200, initialEnableResponse.getCode());

        BasicClassicHttpResponse enabledResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/api/string/echo"), enabledResponse, TestHttpContexts.newContext());
        Assertions.assertEquals(200, enabledResponse.getCode());

        BasicClassicHttpResponse disableResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(
                new BasicClassicHttpRequest("PUT", "/admin/controllers/string/enabled?value=false"),
                disableResponse,
                TestHttpContexts.newContext());
        Assertions.assertEquals(200, disableResponse.getCode());
        Assertions.assertTrue(TestResponseUtil.body(disableResponse).contains("\"enabled\":false"));

        BasicClassicHttpResponse whileDisabled = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/api/string/echo"), whileDisabled, TestHttpContexts.newContext());
        Assertions.assertEquals(403, whileDisabled.getCode());

        BasicClassicHttpResponse enableResponse = new BasicClassicHttpResponse(200);
        dispatcher.handle(
                new BasicClassicHttpRequest("PUT", "/admin/controllers/string/enabled?value=true"),
                enableResponse,
                TestHttpContexts.newContext());
        Assertions.assertEquals(200, enableResponse.getCode());

        BasicClassicHttpResponse afterEnable = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/api/string/echo"), afterEnable, TestHttpContexts.newContext());
        Assertions.assertEquals(200, afterEnable.getCode());
    }
}
