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

class RoutingParityIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        ControllerRegistry registry = DefaultRegistryFactory.createDefaultRegistry();
        registry.setControllerEnabled("String", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnNotFoundForUnknownTopLevelRoute() throws Exception {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/invalid/route"), response, TestHttpContexts.newContext());
        Assertions.assertEquals(404, response.getCode());
    }

    @Test
    void shouldReturnNotFoundForUnknownController() throws Exception {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/api/doesnotexist/test"), response, TestHttpContexts.newContext());
        Assertions.assertEquals(404, response.getCode());
    }

    @Test
    void shouldRouteKnownController() throws Exception {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(new BasicClassicHttpRequest("GET", "/api/string/echo/ping"), response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertEquals("ping", TestResponseUtil.body(response));
    }
}
