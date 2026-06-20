package com.restprovider.integration;

import com.restprovider.controllers.BusinessController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.business.dto.BusinessHealthResponse;
import com.restprovider.domain.business.service.BusinessService;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        BusinessService businessService = () -> new BusinessHealthResponse("UP", "ok");
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new BusinessController(businessService));
        registry.setControllerEnabled("Business", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnHealth() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/business/health");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("\"status\":\"UP\""));
    }

    @Test
    void shouldReturnStatusFromRootRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/business");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(404, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Replace BusinessController"));
    }

    @Test
    void shouldReturnNotFoundForUnknownBusinessRoute() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/business/echo?message=hello");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(404, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Unknown Business route"));
    }
}
