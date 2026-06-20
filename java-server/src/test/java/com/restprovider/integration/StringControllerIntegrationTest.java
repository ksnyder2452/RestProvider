package com.restprovider.integration;

import com.restprovider.controllers.StringController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StringControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new StringController());
        registry.setControllerEnabled("String", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldEchoPlainText() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/string/echo/hello");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertEquals("hello", TestResponseUtil.body(response));
    }

    @Test
    void shouldCompareStringsAndReturn406WhenNotMatched() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
            "GET",
            "/api/string/compare?left=alpha&right=beta&ignoreCase=No");

        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(406, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"matched\":\"No\""));
    }

    @Test
    void shouldHashString() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/string/sha256?plainText=abc");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TestResponseUtil.body(response));
    }

    @Test
    void shouldRejectCompareWhenInputsMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/string/compare?left=alpha");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("string2"));
    }
}
