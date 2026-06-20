package com.restprovider.integration;

import com.restprovider.controllers.SnowflakeController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import java.util.Map;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnowflakeControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        SnowflakeController.HttpExecutor executor = (endpoint, form) -> {
            if (!"https://login.example/token".equals(endpoint)) {
                return "bad endpoint";
            }
            return "{\"access_token\":\"abc\",\"scope\":\"" + form.getOrDefault("scope", "") + "\"}";
        };
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new SnowflakeController(validator, executor));
        registry.setControllerEnabled("Snowflake", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectRequestWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/snowflake/token");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldReturnTokenResultWhenPasscodeIsValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/snowflake/token");
        request.addHeader("passCode", "valid-passcode");
        request.addHeader("az_token_endpoint", "https://login.example/token");
        request.addHeader("az_scope", "scope:read");
        request.addHeader("oauth_client_id", "id1");
        request.addHeader("oauth_client_secret", "sec1");
        request.addHeader("az_user", "u1");
        request.addHeader("az_password", "p1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("access_token"));
    }

    @Test
    void shouldSupportOauthTokenAliasWithQueryString() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/snowflake/oauth/token?passCode=valid-passcode&tokenEndpoint=https://login.example/token"
                        + "&scope=scope:read&clientId=id1&clientSecret=sec1&user=u1&pwd=p1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("scope:read"));
    }

    @Test
    void shouldReturnBadRequestWhenRequiredFieldsMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/snowflake/token");
        request.addHeader("passCode", "valid-passcode");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("Missing required parameter"));
    }
}
