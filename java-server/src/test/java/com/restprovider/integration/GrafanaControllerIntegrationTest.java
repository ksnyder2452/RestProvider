package com.restprovider.integration;

import com.restprovider.controllers.GrafanaController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrafanaControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        GrafanaController.TokenProvider tokenProvider = () -> "token-1";
        GrafanaController.HttpInvoker invoker = (method, endpoint, token) -> "{\"status\":\"ok\"}";

        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new GrafanaController(validator, tokenProvider, invoker));
        registry.setControllerEnabled("Grafana", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldReturnPasscodeFailureWhenMissingPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/grafana");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertEquals("Passcode failure", TestResponseUtil.body(response));
    }

    @Test
    void shouldWriteOutputWhenPasscodeValid() throws Exception {
        String project = "grafana_" + System.nanoTime();
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/grafana");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("projectName", project);
        request.addHeader("grafanaRequest", "dashboards/home");
        request.addHeader("apiVersion", "1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path output = Path.of(System.getProperty("user.dir"), "data_files", "temp", project, "grafana_get_response.txt");
        Assertions.assertTrue(Files.exists(output));
        Assertions.assertTrue(Files.readString(output, StandardCharsets.UTF_8).contains("status"));
    }

    @Test
    void shouldSupportRequestAliasAndQueryString() throws Exception {
        String project = "grafana_" + System.nanoTime();
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/grafana/request?passCode=valid-passcode&project=" + project
                        + "&request=dashboards/home&version=1&token=abc-token");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Path output = Path.of(System.getProperty("user.dir"), "data_files", "temp", project, "grafana_get_response.txt");
        Assertions.assertTrue(Files.exists(output));
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/grafana");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
