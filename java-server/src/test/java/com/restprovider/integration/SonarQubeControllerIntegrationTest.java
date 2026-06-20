package com.restprovider.integration;

import com.restprovider.controllers.SonarQubeController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SonarQubeControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        SonarQubeController.HttpExecutor executor = (endpoint, apiToken, query) -> "{\"data\":{\"ok\":true}}";
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new SonarQubeController(validator, executor));
        registry.setControllerEnabled("SonarQube", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectRequestWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/sonarqube");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldRunGraphQlWhenPasscodeValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/sonarqube");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("serverName", "sonar.example");
        request.addHeader("graphQLQuery", "{project{name}}");
        request.addHeader("sonarqubeApiToken", "t1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("RowCount"));
    }

    @Test
    void shouldSupportGraphqlAliasAndBaseUrl() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/sonarqube/graphql?passCode=valid-passcode&baseUrl=https://sonar.example:9000"
                        + "&query=%7Bproject%7Bname%7D%7D&token=t1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("RowCount"));
    }

    @Test
    void shouldReturnBadRequestWhenQueryMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/api/sonarqube");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("serverName", "sonar.example");
        request.addHeader("sonarqubeApiToken", "t1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
