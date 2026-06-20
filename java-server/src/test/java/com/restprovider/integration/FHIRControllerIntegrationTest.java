package com.restprovider.integration;

import com.restprovider.controllers.FHIRController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FHIRControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        FHIRController.CommandRunner commandRunner = (command, args) -> "token-123";
        FHIRController.HttpInvoker httpInvoker = (method, endpoint, token, payload, contentType) ->
            "{\"method\":\"" + method + "\",\"endpoint\":\"" + endpoint
                + "\",\"payload\":\"" + payload + "\",\"ok\":true}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new FHIRController(validator, commandRunner, httpInvoker));
        registry.setControllerEnabled("FHIR", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/fhir");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldRunGetWhenPasscodeValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/fhir");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("objectType", "Patient");
        request.addHeader("fhirPaaSService", "demo-fhir");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("GET"));
    }

    @Test
    void shouldSupportRouteBasedResourcePath() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/fhir/Patient/123");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("fhirPaaSService", "demo-fhir");
        request.addHeader("fhirToken", "abc-token");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Patient/123"));
    }

    @Test
    void shouldSupportPostWithPayloadAndBaseUrl() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/fhir");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("objectType", "Observation");
        request.addHeader("fhirBaseUrl", "https://example.fhir.service/");
        request.addHeader("fhirToken", "abc-token");
        request.addHeader("payload", "{\"resourceType\":\"Observation\"}");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Observation"));
        Assertions.assertTrue(TestResponseUtil.body(response).contains("POST"));
    }
}
