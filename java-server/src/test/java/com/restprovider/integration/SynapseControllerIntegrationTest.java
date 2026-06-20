package com.restprovider.integration;

import com.restprovider.controllers.SynapseController;
import com.restprovider.core.ControllerDispatcher;
import com.restprovider.core.ControllerRegistry;
import com.restprovider.domain.security.PasscodeValidator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SynapseControllerIntegrationTest {
    private ControllerDispatcher dispatcher;

    @BeforeEach
    void setup() {
        PasscodeValidator validator = passCode -> "valid-passcode".equals(passCode);
        SynapseController.CommandRunner runner = (command, args) -> "synapse query done";
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new SynapseController(validator, runner));
        registry.setControllerEnabled("Synapse", true);
        dispatcher = new ControllerDispatcher(registry);
    }

    @Test
    void shouldRejectRequestWithoutPasscode() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/api/synapse");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void shouldExecuteSynapseQueryWhenPasscodeValid() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
            "POST",
            "/api/synapse/query?passcode=valid-passcode&project=syn_proj&testCase=tc1&sql=select%201&server=syn-server&database=db1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("synapse query done"));
    }

    @Test
    void shouldRejectSynapseQueryWhenSqlMissing() throws Exception {
        BasicClassicHttpRequest request = new BasicClassicHttpRequest(
                "GET",
                "/api/synapse/query?passcode=valid-passcode&project=syn_proj&server=syn-server&database=db1");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);

        dispatcher.handle(request, response, TestHttpContexts.newContext());

        Assertions.assertEquals(400, response.getCode());
        Assertions.assertTrue(TestResponseUtil.body(response).contains("sql_statement"));
    }
}
